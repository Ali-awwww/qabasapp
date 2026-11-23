-- ق55: المراجع (support admin) يحتاج تحديث ظهور ريلز لا يملكه.
-- سياسات reels الحية (202610050003) تسمح بالتحديث للمالك فقط، فيُرفض
-- toggleReelActive من المراجع بـ 42501، فكان reviewModeration يحدّث صف
-- moderation_queue (موافقة/رفض) ثم يفشل ضبط المقطع: المرفوض يبقى ظاهراً
-- علناً بلا عنصر معلّق لإعادة المحاولة، والموافَق على مقطع مخفي يبقى مخفياً.
-- هذه السياسة تفتح التحديث للمسؤولين عبر qabas_is_support_admin() (نفس
-- الدالة الحارسة لسياسات moderation_queue في 20261122020000).
-- العميل لا يحدّث من reels إلا is_active أصلاً (toggleReelActive)، وترتيب
-- الخطوتين في reviewModeration صار: الضبط أولاً ثم الصف، فلا نصف قرار.
-- يُطبَّق في Supabase SQL Editor بعد الدفع (الترحيلات لا تُطبَّق من الكود).

drop policy if exists reels_moderator_update on public.reels;
create policy reels_moderator_update
    on public.reels
    for update
    to authenticated
    using (public.qabas_is_support_admin())
    with check (public.qabas_is_support_admin());
