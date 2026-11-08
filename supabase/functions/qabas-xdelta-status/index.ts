// Read-only status endpoint for an authenticated user's on-demand delta job.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { autoRefreshToken: false, persistSession: false },
});
function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status, headers: { ...cors, "Content-Type": "application/json; charset=utf-8" },
  });
}
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  try {
    const bearer = (req.headers.get("Authorization") || "").match(/^Bearer\s+(.+)$/i)?.[1];
    if (!bearer) return json({ error: "authentication_required" }, 401);
    const { data: auth, error: authError } = await admin.auth.getUser(bearer);
    if (authError || !auth.user) return json({ error: "authentication_required" }, 401);
    const body = await req.json();
    const id = typeof body.job_id === "string" ? body.job_id : "";
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id)) {
      return json({ error: "invalid_job_id" }, 400);
    }
    const { data: job, error } = await admin.from("app_update_delta_jobs")
      .select("id,status,base_version_code,target_version_code,delta_url,delta_size_bytes,delta_sha256_hash,error_code,created_at,updated_at,expires_at")
      .eq("id", id).eq("user_id", auth.user.id).maybeSingle();
    if (error) throw error;
    if (!job) return json({ error: "job_not_found" }, 404);
    return json({ job });
  } catch (error) {
    console.error("XDelta status lookup failed", error instanceof Error ? error.message : "unknown_error");
    return json({ error: "request_failed" }, 500);
  }
});
