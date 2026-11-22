# تطبيق جداول نقاط المجتمع والمراجعات الصادقة — `user_points` / `points_ledger` / `review_helpful_votes` / `review_reports`

> لا يُطبَّق من الجهاز (لا Supabase CLI ولا psql هنا). يُطبَّق يدوياً من
> لوحة تحكم Supabase. حتى قبل التطبيق يعمل كل شيء محلياً: النقاط
> تُحفظ في SharedPreferences (`local_user_points`)، والمراجعات تُقرأ
> من العرض العام، وقراءة السحابة الجديدة تعيد فراغاً والمزامنة تفشل
> بصمت (نمط `getMishkatReminderContent`).

## الخطوات

1. افتح مشروع Qabas في [لوحة تحكم Supabase](https://supabase.com/dashboard)
   ثم **SQL Editor** ← **New query**.
2. الصق كامل محتوى الملف `supabase/migrations/20261122140000_community_points.sql`
   ثم اضغط **Run**.
3. تحقّق من نجاح التطبيق بهذا الاستعلام (يجب أن يعيد 4 صفوف):

```sql
select table_name
from information_schema.tables
where table_schema = 'public'
  and table_name in
    ('user_points', 'points_ledger', 'review_helpful_votes', 'review_reports')
order by table_name;
```

4. تحقّق من سياسات RLS (يجب أن تظهر السياسات المذكورة أدناه):

```sql
select tablename, policyname, cmd
from pg_policies
where schemaname = 'public'
  and tablename in
    ('user_points', 'points_ledger', 'review_helpful_votes',
     'review_reports', 'app_reviews')
order by tablename, cmd;
```

| الجدول | السياسات المتوقعة |
|---|---|
| `user_points` | قراءة عامة + إدخال/تحديث للمالك فقط |
| `points_ledger` | قراءة/إدخال للمالك فقط (سجل إلحاقي: بلا تحديث أو حذف) |
| `review_helpful_votes` | قراءة عامة + إدخال/حذف للمالك فقط |
| `review_reports` | إدخال للمالك فقط (بلا قراءة: البلاغات تُراجع لاحقاً) |
| `app_reviews` | قراءة عامة (كما كانت) + إدخال للمصادَق عليه + تحديث/حذف للمالك فقط |

5. إن فشل إنشاء الفهرس الفريد `app_reviews_one_per_user_idx`، فهذا
   يعني أن مستخدماً لديه أكثر من مراجعة. ابحث عن المكررات:

```sql
select user_id, count(*)
from public.app_reviews
where user_id is not null
group by user_id
having count(*) > 1;
```

   ادمج المكررات يدوياً (أبقِ الأحدث واحذف الباقي)، ثم أعد تشغيل
   هذا الجزء فقط من الترحيل:

```sql
create unique index if not exists app_reviews_one_per_user_idx
  on public.app_reviews (user_id)
  where user_id is not null;
```

## ماذا تفعل الجداول

| الجدول | المعنى |
|---|---|
| `user_points` | إجمالي نقاط المجتمع لكل مستخدم + نقاط الأسبوع الحالي |
| `points_ledger` | صف تدقيق لكل منح نقطة (المقدار + السبب + الوقت)؛ منه تُحسب الدوريات الأسبوعية والشهرية |
| `review_helpful_votes` | صوت "مفيد" واحد لكل (مستخدم، مراجعة)؛ العدد المعروض هو عدد الصفوف الحقيقي |
| `review_reports` | بلاغ واحد لكل سطر على مراجعة (بلا واجهة إشراف بعد) |

## ملاحظات

- عمود `users.strikes` القديم لم يُمسّ إطلاقاً: بياناته باقية،
  ولا كود يقرؤه أو يكتبه كنقاط بعد هذا الترحيل.
- لا بيانات أولية (seed) مطلوبة: الصفوف تُنشأ من التطبيق عند أول
  منح نقاط أو أول تصويت أو أول بلاغ بعد تسجيل الدخول.
- النقاط نقاط مجتمع مكتسبة (تصدير فيديو، اختبار، دخول يومي) كما
  يعرّفها `PointsManager`، وليست منافسة عبادة: لا ترتيب على الصلاة
  ولا منافسة على الحسنات.
- الجداول اختيارية للتشغيل: غيابها لا يكسر التطبيق إطلاقاً.
