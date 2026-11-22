# تطبيق جدول حالة قراءة المكتبة — `library_reading_state`

> لا يُطبَّق من الجهاز (لا Supabase CLI ولا psql هنا). يُطبَّق يدوياً من
> لوحة تحكم Supabase. حتى قبل التطبيق يعمل كل شيء محلياً: القراءة
> تُحفظ في SharedPreferences (`qabas_library_reading`)، وقراءة السحابة
> تعيد فراغاً والمزامنة تفشل بصمت (نمط `getMishkatReminderContent`).

## الخطوات

1. افتح مشروع Qabas في [لوحة تحكم Supabase](https://supabase.com/dashboard)
   ثم **SQL Editor** ← **New query**.
2. الصق كامل محتوى الملف `supabase/migrations/20261122130000_library_reading_state.sql`
   ثم اضغط **Run**.
3. تحقّق من نجاح التطبيق بهذا الاستعلام (يجب أن يعيد صفاً واحداً يصف الجدول):

```sql
select table_name
from information_schema.tables
where table_schema = 'public'
  and table_name = 'library_reading_state';
```

4. تحقّق من سياسات RLS (يجب أن تظهر 4 سياسات owner-only: select/insert/update/delete):

```sql
select policyname, cmd
from pg_policies
where schemaname = 'public'
  and tablename = 'library_reading_state'
order by cmd;
```

## ماذا يفعل الجدول

| العمود | المعنى |
|---|---|
| `user_id` | مالك الصف (Supabase Auth)، مفتاح مشترك مع `book_id` |
| `book_id` | معرّف الكتاب (`sahih_al_bukhari`، `al_daa_wal_dawaa`، …) |
| `last_chapter` / `last_section` | آخر موضع قراءة (فصل + مقطع) |
| `progress` | نسبة الإتمام 0–100 |
| `bookmarked` | إشارة مرجعية على مستوى الكتاب |
| `updated_at` | آخر تحديث |

## ملاحظات

- لا بيانات أولية (seed) مطلوبة: الصفوف تُنشأ من التطبيق عند أول قراءة
  أو أول إشارة مرجعية بعد تسجيل الدخول.
- الجدول اختياري للتشغيل: غيابه لا يكسر التطبيق إطلاقاً.
