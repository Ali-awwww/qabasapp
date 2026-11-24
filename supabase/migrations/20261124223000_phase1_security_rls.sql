-- المرحلة 1 — الأمان: سياسات RLS محافظة للجداول الثمانية التي كان RLS
-- مفعّلاً عليها بلا أي سياسة (تقرير Supabase 2026-11-24).
-- بلا سياسة = لا وصول عبر PostgREST أصلاً، فهذه الهجرة لا "تفتح" شيئاً إلا
-- ما هو مذكور صراحة أدناه؛ الباقي إثبات صريح للمنع + إلغاء المنح.
--
-- ملاحظات التصميم (مثبتة من الكود قبل الكتابة):
-- * app_secrets و platform_api_keys و app_update_delta_cache: لا يلمسها أي
--   عميل؛ تصلها دوال Edge بدور الخدمة فقط (qabas-gateway و qabas-xdelta-*
--   و replace_github_token). بلا سياسات عمداً: المنع الافتراضي هو السياسة.
-- * promo_codes: التطبيق يقرأ الأكواد النشطة مباشرة (getValidPromoCodes في
--   SupabaseServices.kt عبر GiftManager) فأُبقيت قراءة النشط فقط للمصادَق
--   عليه. الكتابة مغلقة عمداً: savePromoCode كان upsert مباشراً يتيح لأي
--   مستخدم مصادَق عليه سكّ أكواد بقيم اعتباطية؛ إنشاء الأكواد يحتاج مسار
--   مالك فقط (متابعة لاحقة)، وهو مغلق حياً أصلاً بانعدام السياسات.
-- * promo_code_redemptions: الكتابة عبر RPCs ذات SECURITY DEFINER فقط
--   (reserve/release/confirm_promo_code)؛ القراءة لصفوف المالك فقط.
-- * reel_shares: الكتابة عبر RPC record_reel_share فقط؛ القراءة لصفوف
--   المالك فقط.
-- * bug_reports و user_feedbacks: لا عمود user_id فيهما أصلاً فاستحالت
--   سياسة "مالك"، ولا كود عميل يقرأهما/يكتب فيهما عبر PostgREST إطلاقاً
--   (الإبلاغ الحي عبر dev_bug_reports). بلا سياسات عمداً + إلغاء المنح.
-- * دوال العدادات الثلاث في آخر الملف trigger-only ذات SECURITY DEFINER؛
--   إلغاء EXECUTE عنها آمن لأن المحفز لا يحتاج صلاحية EXECUTE، وهي سابقة
--   معمول بها في 20261114080251 لـ sync_reel_likes_count.

-- ==============================================================================
-- 1. app_secrets — أسرار خادمية (منها github_token). لا وصول عبر PostgREST.
-- ==============================================================================
alter table public.app_secrets enable row level security;
revoke all on table public.app_secrets from public, anon, authenticated;
grant all on table public.app_secrets to service_role;
-- لا سياسات عمداً: المنع الافتراضي. الوصول عبر service_role والدوال فقط.

-- ==============================================================================
-- 2. platform_api_keys — مفاتيح المنصة المشفرة. لا وصول عبر PostgREST.
-- ==============================================================================
alter table public.platform_api_keys enable row level security;
revoke all on table public.platform_api_keys from public, anon, authenticated;
grant all on table public.platform_api_keys to service_role;
-- لا سياسات عمداً: المنع الافتراضي. الوصول عبر qabas-gateway فقط.

-- ==============================================================================
-- 3. app_update_delta_cache — كاش الدلتا المشترك. دوال Edge فقط.
-- ==============================================================================
alter table public.app_update_delta_cache enable row level security;
revoke all on table public.app_update_delta_cache from public, anon, authenticated;
grant all on table public.app_update_delta_cache to service_role;
-- لا سياسات عمداً: المنع الافتراضي. الوصول عبر qabas-xdelta-* فقط.

-- ==============================================================================
-- 4. promo_codes — قراءة الأكواد النشطة للمصادَق عليه فقط، بلا كتابة.
-- ==============================================================================
alter table public.promo_codes enable row level security;
-- إسقاط أي بقايا من سياسات master_schema.sql العامة إن وُجدت حياً.
drop policy if exists "Public select policy on promo_codes" on public.promo_codes;
drop policy if exists "Public insert policy on promo_codes" on public.promo_codes;
drop policy if exists "Public update policy on promo_codes" on public.promo_codes;
drop policy if exists "Public delete policy on promo_codes" on public.promo_codes;
revoke all on table public.promo_codes from public, anon, authenticated;
grant select on table public.promo_codes to authenticated;
grant all on table public.promo_codes to service_role;
drop policy if exists promo_codes_active_read on public.promo_codes;
create policy promo_codes_active_read
    on public.promo_codes for select to authenticated
    using (is_active is true);
-- لا سياسات insert/update/delete للمصادَق عليه: إنشاء الأكواد يحتاج مسار
-- مالك فقط (لم يُبنَ بعد)؛ سكّ الأكواد من العميل مغلق عمداً.

-- ==============================================================================
-- 5. promo_code_redemptions — قراءة صفوف المالك فقط؛ الكتابة عبر RPCs فقط.
-- ==============================================================================
alter table public.promo_code_redemptions enable row level security;
revoke all on table public.promo_code_redemptions from public, anon, authenticated;
grant select on table public.promo_code_redemptions to authenticated;
grant all on table public.promo_code_redemptions to service_role;
drop policy if exists promo_redemptions_select_own on public.promo_code_redemptions;
create policy promo_redemptions_select_own
    on public.promo_code_redemptions for select to authenticated
    using (user_id = auth.uid()::text);
-- لا سياسات كتابة: الحجز/التأكيد/الإلغاء عبر reserve/release/confirm_promo_code.

-- ==============================================================================
-- 6. reel_shares — قراءة صفوف المالك فقط؛ الكتابة عبر RPC فقط.
-- ==============================================================================
alter table public.reel_shares enable row level security;
revoke all on table public.reel_shares from public, anon, authenticated;
grant select on table public.reel_shares to authenticated;
grant all on table public.reel_shares to service_role;
drop policy if exists reel_shares_select_own on public.reel_shares;
create policy reel_shares_select_own
    on public.reel_shares for select to authenticated
    using (user_id = auth.uid()::text);
-- لا سياسة insert: المشاركة تُسجَّل عبر record_reel_share فقط.

-- ==============================================================================
-- 7. bug_reports — مغلق عبر PostgREST (بلا سياسات عمداً).
-- ==============================================================================
alter table public.bug_reports enable row level security;
drop policy if exists "Public select policy on bug_reports" on public.bug_reports;
drop policy if exists "Public insert policy on bug_reports" on public.bug_reports;
drop policy if exists "Public update policy on bug_reports" on public.bug_reports;
drop policy if exists "Public delete policy on bug_reports" on public.bug_reports;
revoke all on table public.bug_reports from public, anon, authenticated;
grant all on table public.bug_reports to service_role;
-- لا سياسات عمداً: لا عمود user_id فلا سياسة مالك ممكنة، ولا عميل يستخدم
-- الجدول (الإبلاغ الحي عبر dev_bug_reports). المنع الافتراضي هو السياسة.

-- ==============================================================================
-- 8. user_feedbacks — مغلق عبر PostgREST (بلا سياسات عمداً).
-- ==============================================================================
alter table public.user_feedbacks enable row level security;
drop policy if exists "Public select policy on user_feedbacks" on public.user_feedbacks;
drop policy if exists "Public insert policy on user_feedbacks" on public.user_feedbacks;
drop policy if exists "Public update policy on user_feedbacks" on public.user_feedbacks;
drop policy if exists "Public delete policy on user_feedbacks" on public.user_feedbacks;
revoke all on table public.user_feedbacks from public, anon, authenticated;
grant all on table public.user_feedbacks to service_role;
-- لا سياسات عمداً: نفس سبب bug_reports أعلاه.

-- ==============================================================================
-- 9. دوال عدادات trigger-only ذات SECURITY DEFINER: إلغاء الاستدعاء المباشر.
-- الاستدعاء المباشر لها خارج المحفز no-op أصلاً (TG_OP فارغ)، لكن بقاء
-- EXECUTE للعامة على دالة SECURITY DEFINER سطح هجوم بلا فائدة. المحفزات
-- لا تحتاج صلاحية EXECUTE فتستمر العدادات بالعمل كما هي.
-- ==============================================================================
revoke all on function public.handle_reel_like_counter() from public, anon, authenticated;
revoke all on function public.handle_reel_comment_counter() from public, anon, authenticated;
revoke all on function public.sync_reel_likes_count() from public, anon, authenticated;
