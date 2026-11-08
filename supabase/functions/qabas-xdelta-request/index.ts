// Authenticated on-demand XDelta request endpoint. GitHub credentials stay server-side.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const admin = createClient(supabaseUrl, serviceRoleKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});
function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status, headers: { ...cors, "Content-Type": "application/json; charset=utf-8" },
  });
}
function validSha(value: unknown): value is string {
  return typeof value === "string" && /^[a-fA-F0-9]{64}$/.test(value);
}
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  try {
    const authHeader = req.headers.get("Authorization") || "";
    const bearer = authHeader.match(/^Bearer\s+(.+)$/i)?.[1];
    if (!bearer) return json({ error: "authentication_required" }, 401);
    const { data: authData, error: authError } = await admin.auth.getUser(bearer);
    if (authError || !authData.user) return json({ error: "authentication_required" }, 401);
    const userId = authData.user.id;

    const body = await req.json();
    const baseVersionCode = Number(body.base_version_code);
    const baseSha = typeof body.base_sha256_hash === "string" ? body.base_sha256_hash.toLowerCase() : "";
    if (!Number.isSafeInteger(baseVersionCode) || baseVersionCode <= 0 || !validSha(baseSha)) {
      return json({ error: "invalid_base_identity" }, 400);
    }

    // Per-user request throttle: at most 5 requests in a rolling 10-minute window.
    const cutoff = new Date(Date.now() - 10 * 60 * 1000).toISOString();
    const { count, error: throttleError } = await admin.from("app_update_delta_jobs")
      .select("id", { count: "exact", head: true })
      .eq("user_id", userId).gte("created_at", cutoff);
    if (throttleError) throw throttleError;
    if ((count ?? 0) >= 5) return json({ error: "rate_limited" }, 429);

    const { data: base, error: baseError } = await admin.from("app_updates")
      .select("version_code, sha256_hash, release_tag, download_url")
      .eq("version_code", baseVersionCode).maybeSingle();
    if (baseError) throw baseError;
    if (!base || String(base.sha256_hash || "").toLowerCase() !== baseSha ||
        typeof base.release_tag !== "string" || !base.release_tag.trim()) {
      return json({ error: "base_not_approved" }, 409);
    }

    const { data: target, error: targetError } = await admin.from("app_updates")
      .select("version_code, sha256_hash, release_tag, download_url, file_size_bytes")
      .eq("is_active", true).order("version_code", { ascending: false }).limit(1).maybeSingle();
    if (targetError) throw targetError;
    if (!target || !validSha(String(target.sha256_hash || "")) ||
        typeof target.release_tag !== "string" || !target.release_tag.trim()) {
      return json({ error: "approved_target_unavailable" }, 503);
    }
    if (baseVersionCode >= Number(target.version_code)) return json({ error: "base_not_older_than_target" }, 409);

    const { data: existing, error: existingError } = await admin.from("app_update_delta_jobs")
      .select("id,status,delta_url,delta_size_bytes,delta_sha256_hash,expires_at")
      .eq("user_id", userId).eq("base_sha256_hash", baseSha)
      .eq("target_sha256_hash", String(target.sha256_hash).toLowerCase())
      .maybeSingle();
    if (existingError) throw existingError;
    if (existing && existing.status !== "failed" &&
        (existing.status !== "ready" || (existing.expires_at && Date.parse(existing.expires_at) > Date.now()))) {
      return json({ job: existing, reused: true });
    }

    // Reuse a previously verified patch for this exact base/target byte pair.
    const { data: cached, error: cacheError } = await admin.from("app_update_delta_cache")
      .select("delta_url,delta_size_bytes,delta_sha256_hash,expires_at")
      .eq("base_sha256_hash", baseSha)
      .eq("target_sha256_hash", String(target.sha256_hash).toLowerCase())
      .gt("expires_at", new Date().toISOString())
      .maybeSingle();
    if (cacheError) throw cacheError;
    if (cached) {
      const cachedJobPayload = {
        user_id: userId,
        base_version_code: baseVersionCode,
        base_sha256_hash: baseSha,
        target_version_code: Number(target.version_code),
        target_sha256_hash: String(target.sha256_hash).toLowerCase(),
        status: "ready",
        delta_url: cached.delta_url,
        delta_size_bytes: cached.delta_size_bytes,
        delta_sha256_hash: cached.delta_sha256_hash,
        expires_at: cached.expires_at,
        error_code: null,
        updated_at: new Date().toISOString(),
      };
      const { data: cachedJob, error: cachedJobError } = await admin.from("app_update_delta_jobs")
        .upsert({ ...cachedJobPayload, ...(existing ? { id: existing.id } : {}) },
          { onConflict: "user_id,base_sha256_hash,target_sha256_hash" })
        .select("id,status,base_version_code,target_version_code,delta_url,delta_size_bytes,delta_sha256_hash,expires_at")
        .single();
      if (cachedJobError) throw cachedJobError;
      return json({ job: cachedJob, reused: true, cache_hit: true });
    }

    const { data: secret, error: secretError } = await admin.from("app_secrets")
      .select("secret_value").eq("secret_key", "github_token").eq("is_active", true)
      .limit(1).maybeSingle();
    if (secretError) throw secretError;
    const githubToken = typeof secret?.secret_value === "string" ? secret.secret_value.trim() : "";
    if (!githubToken || githubToken.startsWith("ghp_") && githubToken.length < 30) {
      return json({ error: "server_dispatch_not_configured" }, 503);
    }

    const jobPayload = {
      user_id: userId,
      base_version_code: baseVersionCode,
      base_sha256_hash: baseSha,
      target_version_code: Number(target.version_code),
      target_sha256_hash: String(target.sha256_hash).toLowerCase(),
      status: "queued",
      delta_url: null,
      delta_size_bytes: null,
      delta_sha256_hash: null,
      error_code: null,
      expires_at: null,
      updated_at: new Date().toISOString(),
    };
    const { data: job, error: insertError } = await admin.from("app_update_delta_jobs")
      .upsert({ ...jobPayload, ...(existing ? { id: existing.id } : {}) },
        { onConflict: "user_id,base_sha256_hash,target_sha256_hash" })
      .select("id,status,base_version_code,target_version_code")
      .single();
    if (insertError) throw insertError;

    const dispatch = await fetch("https://api.github.com/repos/Ali-awwww/qabasapp/actions/workflows/xdelta--updates.yml/dispatches", {
      method: "POST",
      headers: {
        Accept: "application/vnd.github+json",
        Authorization: `Bearer ${githubToken}`,
        "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/json",
        "User-Agent": "Qabas-OnDemandDelta",
      },
      body: JSON.stringify({
        ref: "feature/issue-69-on-demand-xdelta-secure",
        inputs: { target_tag: target.release_tag, previous_tag: base.release_tag, job_id: job.id, expected_base_sha256: baseSha, expected_target_sha256: String(target.sha256_hash).toLowerCase() },
      }),
    });
    if (dispatch.status !== 204) {
      await admin.from("app_update_delta_jobs").update({
        status: "failed", error_code: "github_dispatch_failed", updated_at: new Date().toISOString(),
      }).eq("id", job.id);
      return json({ error: "dispatch_failed" }, 502);
    }
    const { data: buildingJob, error: buildingError } = await admin.from("app_update_delta_jobs")
      .update({ status: "building", updated_at: new Date().toISOString() })
      .eq("id", job.id).select("id,status,base_version_code,target_version_code").single();
    if (buildingError) throw buildingError;
    return json({ job: buildingJob, reused: false }, 202);
  } catch (error) {
    console.error("XDelta request failed", error instanceof Error ? error.message : "unknown_error");
    return json({ error: "request_failed" }, 500);
  }
});
