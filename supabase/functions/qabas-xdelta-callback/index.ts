// Trusted GitHub Actions callback for on-demand delta job completion.
// The service-role credential is accepted only from the repository's protected workflow secret.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
const callbackSecret = Deno.env.get("XDELTA_CALLBACK_SECRET") || "";
const admin = createClient(Deno.env.get("SUPABASE_URL")!, serviceRoleKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});
const json = (body: unknown, status = 200) => Response.json(body, {
  status, headers: { ...cors, "Content-Type": "application/json; charset=utf-8" },
});
const validSha = (value: unknown): value is string =>
  typeof value === "string" && /^[a-fA-F0-9]{64}$/.test(value);
const validJobId = (value: unknown): value is string =>
  typeof value === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);

Deno.serve(async req => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  if (!callbackSecret || req.headers.get("Authorization") !== `Bearer ${callbackSecret}`) {
    return json({ error: "callback_authentication_failed" }, 401);
  }

  try {
    const body = await req.json();
    const jobId = body.job_id;
    const targetTag = typeof body.target_tag === "string" ? body.target_tag.trim() : "";
    const baseSha = typeof body.expected_base_sha256 === "string" ? body.expected_base_sha256.toLowerCase() : "";
    const targetSha = typeof body.expected_target_sha256 === "string" ? body.expected_target_sha256.toLowerCase() : "";
    const status = body.status;
    const errorCode = typeof body.error_code === "string" ? body.error_code : null;

    if ((!validJobId(jobId) && !targetTag) || !validSha(baseSha) || !validSha(targetSha) ||
        !["ready", "failed"].includes(status)) {
      return json({ error: "invalid_callback_payload" }, 400);
    }

    const update: Record<string, unknown> = {
      status,
      error_code: status === "failed" ? (errorCode || "delta_generation_failed") : null,
      updated_at: new Date().toISOString(),
    };
    if (status === "ready") {
      const deltaUrl = typeof body.delta_url === "string" ? body.delta_url : "";
      const deltaSize = Number(body.delta_size_bytes);
      const deltaSha = typeof body.delta_sha256_hash === "string" ? body.delta_sha256_hash.toLowerCase() : "";
      const expectedPrefix = "https://github.com/Ali-awwww/qabasapp/releases/download/";
      if (!deltaUrl.startsWith(expectedPrefix) || deltaUrl.length > 2048 ||
          !Number.isSafeInteger(deltaSize) || deltaSize <= 0 || !validSha(deltaSha)) {
        return json({ error: "invalid_ready_metadata" }, 400);
      }
      update.delta_url = deltaUrl;
      update.delta_size_bytes = deltaSize;
      update.delta_sha256_hash = deltaSha;
      update.expires_at = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString();
    }

    const cacheExpiresAt = status === "ready"
      ? String(update.expires_at)
      : new Date().toISOString();
    const cachePayload: Record<string, unknown> = {
      base_sha256_hash: baseSha,
      target_sha256_hash: targetSha,
      status,
      job_id: validJobId(jobId) ? jobId : null,
      delta_url: status === "ready" ? update.delta_url : null,
      delta_size_bytes: status === "ready" ? update.delta_size_bytes : null,
      delta_sha256_hash: status === "ready" ? update.delta_sha256_hash : null,
      expires_at: cacheExpiresAt,
    };
    const { error: cacheWriteError } = await admin.from("app_update_delta_cache").upsert(
      cachePayload, { onConflict: "base_sha256_hash,target_sha256_hash" },
    );
    if (cacheWriteError) throw cacheWriteError;

    // The normal release workflow has no per-user job. Promote its verified
    // patch metadata to the approved app_updates row so the next app check
    // can use xdelta directly instead of dispatching a duplicate job.
    if (status === "ready" && targetTag) {
      const { data: target, error: targetError } = await admin.from("app_updates")
        .select("id,version_code,sha256_hash")
        .eq("release_tag", targetTag)
        .maybeSingle();
      if (targetError) throw targetError;
      if (!target || String(target.sha256_hash || "").toLowerCase() !== targetSha) {
        return json({ error: "target_release_not_found_or_digest_mismatch" }, 409);
      }
      const { data: base, error: baseError } = await admin.from("app_updates")
        .select("version_code")
        .eq("sha256_hash", baseSha)
        .maybeSingle();
      if (baseError) throw baseError;
      if (!base || Number(base.version_code) >= Number(target.version_code)) {
        return json({ error: "base_release_not_found_or_not_older" }, 409);
      }
      const { error: promoteError } = await admin.from("app_updates").update({
        delta_url: update.delta_url,
        delta_size_bytes: update.delta_size_bytes,
        delta_sha256_hash: update.delta_sha256_hash,
        base_version_code: base.version_code,
        base_sha256_hash: baseSha,
      }).eq("id", target.id);
      if (promoteError) throw promoteError;
    }

    // Complete every user job waiting for this exact shared digest pair.
    let jobsUpdated = 0;
    if (validJobId(jobId)) {
      const { data, error } = await admin.from("app_update_delta_jobs").update({
        ...update,
        expires_at: status === "ready" ? update.expires_at : null,
      }).eq("base_sha256_hash", baseSha).eq("target_sha256_hash", targetSha)
        .in("status", ["queued", "building"]).select("id,status");
      if (error) throw error;
      jobsUpdated = data?.length ?? 0;
      if (jobsUpdated === 0 && !targetTag) {
        return json({ error: "jobs_not_found_or_already_finalized" }, 404);
      }
    }
    return json({ jobs_updated: jobsUpdated, status, app_update_promoted: status === "ready" && !!targetTag });
  } catch (error) {
    console.error("XDelta callback failed", error instanceof Error ? error.message : "unknown_error");
    return json({ error: "callback_failed" }, 500);
  }
});
