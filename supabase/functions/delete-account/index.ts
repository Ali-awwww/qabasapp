import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const admin = createClient(supabaseUrl, serviceRoleKey);

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: {
    "Content-Type": "application/json",
    "Access-Control-Allow-Origin": "*",
  },
});

// User-owned tables keyed by user_id. Each delete is attempted independently
// and reported separately: a missing table or a denied delete must never
// abort the Auth-user deletion, and the client must never claim completion
// from a partial result.
// ق156: أُضيفت الجداول المملوكة للمستخدم التي كانت تُترك بعد الحذف
// (نقاطه وسجله وأصواته وأحداثه ومهامه وتوليداته ومراجعاته ومشاريعه)
// — القائمة هنا مرآة deleteOwnAccountRows في SupabaseServices.kt.
const USER_TABLES = [
  "user_settings",
  "user_api_keys",
  "user_push_tokens",
  "saved_items",
  "library_reading_state",
  "reel_likes",
  "reel_bookmarks",
  "reel_history",
  "reel_comments",
  "user_points",
  "points_ledger",
  "review_helpful_votes",
  "analytics_events",
  "studio_jobs",
  "ai_generations",
  "app_reviews",
  "projects",
];

// Public content the user published (their reels) is intentionally NOT
// deleted here: it is public content that other users may have interacted
// with. The app's confirmation dialog says this plainly, and the user is
// expected to delete their own reels first if they want them gone.
async function authenticatedUser(req: Request) {
  const token = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
  if (!token) return null;
  const { data, error } = await admin.auth.getUser(token);
  if (error || !data.user) return null;
  return data.user;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: { "Access-Control-Allow-Origin": "*" } });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const user = await authenticatedUser(req);
  if (!user) return json({ error: "unauthorized" }, 401);

  try {
    const payload = await req.json().catch(() => ({}));
    if (payload?.confirm !== true) {
      return json({ ok: false, error: "confirmation_required" }, 400);
    }

    const deleted: Record<string, boolean> = {};
    for (const table of USER_TABLES) {
      const { error } = await admin.from(table).delete().eq("user_id", user.id);
      deleted[table] = !error;
      if (error) console.error(`delete-account: ${table} delete failed`, error.message);
    }

    // The public profile row is keyed by id, not user_id.
    const { error: usersError } = await admin.from("users").delete().eq("id", user.id);
    deleted["users"] = !usersError;
    if (usersError) console.error("delete-account: users delete failed", usersError.message);

    // ق156: صفوف بمفاتيح أخرى — المرآة العامة (profiles) وصف التوثيق
    // (verified_creators) بمفتاح id، والمتابعات (creator_follows)
    // بمفتاحي follower_id وcreator_id معاً.
    const { error: profilesError } = await admin.from("profiles").delete().eq("id", user.id);
    deleted["profiles"] = !profilesError;
    if (profilesError) console.error("delete-account: profiles delete failed", profilesError.message);

    const { error: verifiedError } = await admin.from("verified_creators").delete().eq("id", user.id);
    deleted["verified_creators"] = !verifiedError;
    if (verifiedError) console.error("delete-account: verified_creators delete failed", verifiedError.message);

    const { error: followsAsFollowerError } = await admin.from("creator_follows").delete().eq("follower_id", user.id);
    deleted["creator_follows_as_follower"] = !followsAsFollowerError;
    if (followsAsFollowerError) console.error("delete-account: creator_follows(follower) delete failed", followsAsFollowerError.message);

    const { error: followsAsCreatorError } = await admin.from("creator_follows").delete().eq("creator_id", user.id);
    deleted["creator_follows_as_creator"] = !followsAsCreatorError;
    if (followsAsCreatorError) console.error("delete-account: creator_follows(creator) delete failed", followsAsCreatorError.message);

    // The authoritative step: only the service_role key can delete an Auth
    // user. Rows above are best-effort cleanup; this is the deletion.
    const { error: authError } = await admin.auth.admin.deleteUser(user.id);
    if (authError) {
      console.error("delete-account: auth user delete failed", authError.message);
      return json({ ok: false, error: "auth_delete_failed", deleted }, 500);
    }

    return json({ ok: true, deletedAuthUser: true, deleted });
  } catch (error) {
    console.error("delete-account failed", error instanceof Error ? error.message : "unknown");
    return json({ error: "delete_account_error" }, 500);
  }
});
