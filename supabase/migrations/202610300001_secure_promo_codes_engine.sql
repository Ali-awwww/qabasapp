-- ==============================================================================
-- 🚀 ملف الهجرة المعتمد: 202610300001_secure_promo_codes_engine.sql
-- نظام أكواد الخصم والترقية والهدايا المؤمّن بنمط الحجز الذري ثنائي المراحل
-- ==============================================================================

-- 0. حذف التوقيعات القديمة غير المتطابقة إن وجدت
DROP FUNCTION IF EXISTS public.reserve_promo_code(text);

-- 1. تحديث جدول promo_codes وإضافة القيود
ALTER TABLE public.promo_codes
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS max_total_uses INT NULL,
    ADD COLUMN IF NOT EXISTS max_uses_per_user INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ NULL;

DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_promo_max_user') THEN
        ALTER TABLE public.promo_codes ADD CONSTRAINT chk_promo_max_user CHECK (max_uses_per_user > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_promo_max_total') THEN
        ALTER TABLE public.promo_codes ADD CONSTRAINT chk_promo_max_total CHECK (max_total_uses IS NULL OR max_total_uses > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_promo_positive_value') THEN
        ALTER TABLE public.promo_codes ADD CONSTRAINT chk_promo_positive_value CHECK (value IS NULL OR value >= 0);
    END IF;
END $$;

-- 2. جدول سجلات الحجز والاستهلاك والتدقيق المالي
CREATE TABLE IF NOT EXISTS public.promo_code_redemptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_token UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    idempotency_key UUID NULL,
    code TEXT NOT NULL REFERENCES public.promo_codes(code) ON DELETE RESTRICT,
    user_id TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('RESERVED', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    discount_type TEXT NOT NULL DEFAULT 'PROMO',
    discount_value BIGINT NOT NULL DEFAULT 0,
    order_id TEXT NULL,
    reserved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reserved_until TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '15 minutes'),
    confirmed_at TIMESTAMPTZ NULL
);

-- فهارس الأداء وتفادي تكرار المعاملات
CREATE UNIQUE INDEX IF NOT EXISTS idx_redemptions_idempotency 
    ON public.promo_code_redemptions(idempotency_key) 
    WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_redemptions_order_id 
    ON public.promo_code_redemptions(order_id) 
    WHERE status = 'CONFIRMED';

CREATE INDEX IF NOT EXISTS idx_redemptions_cleanup 
    ON public.promo_code_redemptions(code, status, reserved_until);

CREATE INDEX IF NOT EXISTS idx_redemptions_user_stats 
    ON public.promo_code_redemptions(code, user_id, status);

-- تفعيل RLS وحصر الصلاحيات
ALTER TABLE public.promo_code_redemptions ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.promo_code_redemptions FROM PUBLIC, anon, authenticated;
GRANT ALL ON TABLE public.promo_code_redemptions TO service_role;

-- 3. دالة الحجز المسبق المؤقت (reserve_promo_code)
CREATE OR REPLACE FUNCTION public.reserve_promo_code(p_code text, p_idempotency_key uuid DEFAULT NULL)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_uid text;
    v_code text := upper(trim(p_code));
    v_promo record;
    v_existing record;
    v_active_total int;
    v_active_user int;
    v_token uuid;
    v_until timestamptz;
BEGIN
    v_uid := (SELECT auth.uid()::text);
    IF v_uid IS NULL OR v_uid = '' THEN
        RETURN jsonb_build_object('success', false, 'error_code', 'UNAUTHORIZED', 'message', 'يجب تسجيل الدخول لاستخدام الكود');
    END IF;

    -- 1. فحص مفتاح التكرار مسبقاً إن وجد
    IF p_idempotency_key IS NOT NULL THEN
        SELECT * INTO v_existing 
        FROM public.promo_code_redemptions 
        WHERE idempotency_key = p_idempotency_key;

        IF FOUND THEN
            -- تحقق من تطابق المستخدم والكود
            IF v_existing.user_id <> v_uid THEN
                RETURN jsonb_build_object('success', false, 'error_code', 'IDEMPOTENCY_USER_MISMATCH', 'message', 'مفتاح الطلب مستخدم لحساب آخر');
            END IF;
            IF v_existing.code <> v_code THEN
                RETURN jsonb_build_object('success', false, 'error_code', 'IDEMPOTENCY_CODE_MISMATCH', 'message', 'مفتاح الطلب مستخدم مسبقاً لكود مختلف');
            END IF;

            -- إعادة النتيجة بحسب الحالة
            IF v_existing.status = 'RESERVED' AND v_existing.reserved_until > now() THEN
                RETURN jsonb_build_object(
                    'success', true,
                    'is_retry', true,
                    'reservation_token', v_existing.reservation_token,
                    'code', v_existing.code,
                    'discount_type', v_existing.discount_type,
                    'discount_value', v_existing.discount_value,
                    'reserved_until', v_existing.reserved_until
                );
            ELSIF v_existing.status = 'CONFIRMED' THEN
                RETURN jsonb_build_object('success', false, 'error_code', 'ALREADY_CONFIRMED', 'message', 'تم تأكيد واستهلاك هذا الطلب مسبقاً');
            ELSE
                RETURN jsonb_build_object('success', false, 'error_code', 'IDEMPOTENCY_EXPIRED', 'message', 'انتهت صلاحية محاولة الحجز هذه، يرجى إنشاء طلب جديد');
            END IF;
        END IF;
    END IF;

    -- 2. قفل السجل لمنع التضارب
    SELECT * INTO v_promo 
    FROM public.promo_codes 
    WHERE code = v_code 
    FOR UPDATE;

    IF NOT FOUND OR v_promo.deleted_at IS NOT NULL THEN
        RETURN jsonb_build_object('success', false, 'error_code', 'NOT_FOUND', 'message', 'الكود المدخل غير صالح');
    END IF;

    -- NULL يعامل كـ غير نشط
    IF v_promo.is_active IS NOT TRUE THEN
        RETURN jsonb_build_object('success', false, 'error_code', 'INACTIVE', 'message', 'هذا الكود معطل حالياً');
    END IF;

    IF v_promo.expires_at IS NOT NULL AND v_promo.expires_at <= now() THEN
        RETURN jsonb_build_object('success', false, 'error_code', 'EXPIRED', 'message', 'انتهت صلاحية هذا الكود');
    END IF;

    -- تنظيف الحجوزات المنتهية للكود
    UPDATE public.promo_code_redemptions
    SET status = 'EXPIRED'
    WHERE code = v_code AND status = 'RESERVED' AND reserved_until <= now();

    -- فحص الحد الأقصى للمستخدم
    SELECT count(*) INTO v_active_user
    FROM public.promo_code_redemptions
    WHERE code = v_code 
      AND user_id = v_uid 
      AND (status = 'CONFIRMED' OR (status = 'RESERVED' AND reserved_until > now()));

    IF v_active_user >= coalesce(v_promo.max_uses_per_user, 1) THEN
        RETURN jsonb_build_object('success', false, 'error_code', 'USER_LIMIT_EXCEEDED', 'message', 'لقد استخدمت هذا الكود بالحد الأقصى المسموح لك');
    END IF;

    -- فحص الحد الكلي للاستخدام
    IF v_promo.max_total_uses IS NOT NULL THEN
        SELECT count(*) INTO v_active_total
        FROM public.promo_code_redemptions
        WHERE code = v_code 
          AND (status = 'CONFIRMED' OR (status = 'RESERVED' AND reserved_until > now()));

        IF v_active_total >= v_promo.max_total_uses THEN
            RETURN jsonb_build_object('success', false, 'error_code', 'TOTAL_LIMIT_EXCEEDED', 'message', 'تم استنفاد جميع الحصص المتاحة لهذا الكود');
        END IF;
    END IF;

    -- 3. إنشاء الحجز المؤقت مع حماية سباق الفهرس الفريد
    v_token := gen_random_uuid();
    v_until := now() + interval '15 minutes';

    BEGIN
        INSERT INTO public.promo_code_redemptions (
            reservation_token, idempotency_key, code, user_id, status, discount_type, discount_value, reserved_until
        ) VALUES (
            v_token, p_idempotency_key, v_code, v_uid, 'RESERVED', coalesce(v_promo.type, 'PROMO'), coalesce(v_promo.value, 0), v_until
        );
    EXCEPTION WHEN unique_violation THEN
        -- تم إدراج الحجز بالتزامن عبر طلب مطابق، نعيد قراءة السجل
        IF p_idempotency_key IS NOT NULL THEN
            SELECT * INTO v_existing 
            FROM public.promo_code_redemptions 
            WHERE idempotency_key = p_idempotency_key;

            IF FOUND AND v_existing.status = 'RESERVED' AND v_existing.reserved_until > now() THEN
                RETURN jsonb_build_object(
                    'success', true,
                    'is_retry', true,
                    'reservation_token', v_existing.reservation_token,
                    'code', v_existing.code,
                    'discount_type', v_existing.discount_type,
                    'discount_value', v_existing.discount_value,
                    'reserved_until', v_existing.reserved_until
                );
            END IF;
        END IF;
        RETURN jsonb_build_object('success', false, 'error_code', 'CONCURRENT_REQUEST', 'message', 'حدث تعارض في الطلب، يرجى المحاولة مرة أخرى');
    END;

    RETURN jsonb_build_object(
        'success', true,
        'is_retry', false,
        'reservation_token', v_token,
        'code', v_code,
        'discount_type', coalesce(v_promo.type, 'PROMO'),
        'discount_value', coalesce(v_promo.value, 0),
        'reserved_until', v_until
    );
END;
$$;

-- 4. دالة إلغاء الحجز (release_promo_code)
CREATE OR REPLACE FUNCTION public.release_promo_code(p_token uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_uid text;
    v_row record;
BEGIN
    v_uid := (SELECT auth.uid()::text);
    
    SELECT * INTO v_row 
    FROM public.promo_code_redemptions 
    WHERE reservation_token = p_token AND user_id = v_uid AND status = 'RESERVED'
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'message', 'الحجز غير موجود أو ملغى مسبقاً');
    END IF;

    UPDATE public.promo_code_redemptions
    SET status = 'CANCELLED'
    WHERE id = v_row.id;

    RETURN jsonb_build_object('success', true);
END;
$$;

-- 5. دالة تأكيد الدفع والاستهلاك الفعلي (confirm_promo_code)
CREATE OR REPLACE FUNCTION public.confirm_promo_code(p_token uuid, p_order_id text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_row record;
BEGIN
    IF p_order_id IS NULL OR trim(p_order_id) = '' THEN
        RETURN jsonb_build_object('success', false, 'error_code', 'INVALID_ORDER_ID', 'message', 'معرف الطلب غير صالح');
    END IF;

    SELECT * INTO v_row 
    FROM public.promo_code_redemptions 
    WHERE reservation_token = p_token 
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error_code', 'RESERVATION_NOT_FOUND', 'message', 'الحجز غير موجود');
    END IF;

    -- إعادة المحاولة لنفس الحجز والطلب (Idempotency)
    IF v_row.status = 'CONFIRMED' AND v_row.order_id = p_order_id THEN
        RETURN jsonb_build_object('success', true, 'message', 'تم تأكيد الكود مسبقاً', 'discount_value', v_row.discount_value);
    END IF;

    IF v_row.status <> 'RESERVED' THEN
        RETURN jsonb_build_object('success', false, 'error_code', 'INVALID_STATUS', 'message', 'حالة الحجز غير صالحة للتأكيد');
    END IF;

    IF v_row.reserved_until <= now() THEN
        UPDATE public.promo_code_redemptions SET status = 'EXPIRED' WHERE id = v_row.id;
        RETURN jsonb_build_object('success', false, 'error_code', 'RESERVATION_EXPIRED', 'message', 'انتهت مدة صلاحية حجز الكود');
    END IF;

    -- محاولة التحديث مع التقاط تعارض الفهرس الفريد
    BEGIN
        UPDATE public.promo_code_redemptions
        SET status = 'CONFIRMED',
            order_id = p_order_id,
            confirmed_at = now()
        WHERE id = v_row.id;
    EXCEPTION WHEN unique_violation THEN
        RETURN jsonb_build_object('success', false, 'error_code', 'ORDER_ALREADY_USED', 'message', 'رقم الطلب مستخدم مسبقاً في عملية أخرى');
    END;

    RETURN jsonb_build_object(
        'success', true,
        'code', v_row.code,
        'discount_type', v_row.discount_type,
        'discount_value', v_row.discount_value,
        'order_id', p_order_id
    );
END;
$$;

-- 6. تقييد الصلاحيات
REVOKE ALL ON FUNCTION public.reserve_promo_code(text, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.reserve_promo_code(text, uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.release_promo_code(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.release_promo_code(uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.confirm_promo_code(uuid, text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.confirm_promo_code(uuid, text) TO service_role;
