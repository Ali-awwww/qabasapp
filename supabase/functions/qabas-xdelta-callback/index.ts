// Trusted GitHub Actions callback for on-demand delta job completion.
// The service-role credential is accepted only from the repository's protected workflow secret.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
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
  if (!serviceRoleKey || req.headers.get("Authorization") !== `Bearer ${serviceRoleKey}`) {
    return json({ error: "callback_authentication_failed" }, 401);
  }

  try {
    const body = await req.json();
    const jobId = body.job_id;
    const baseSha = typeof body.expected_base_sha256 === "string" ? body.expected_base_sha256.toLowerCase() : "";
    const targetSha = typeof body.expected_target_sha256 === "string" ? body.expected_target_sha256.toLowerCase() : "";
    const status = body.status;
    const errorCode = typeof body.error_code === "string" ? body.error_code : null;

    if (!validJobId(jobId) || !validSha(baseSha) || !validSha(targetSha) ||
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
      job_id: jobId,
      delta_url: status === "ready" ? update.delta_url : null,
      delta_size_bytes: status === "ready" ? update.delta_size_bytes : null,
      delta_sha256_hash: status === "ready" ? update.delta_sha256_hash : null,
      expires_at: cacheExpiresAt,
    };
    const { error: cacheWriteError } = await admin.from("app_update_delta_cache").upsert(
      cachePayload, { onConflict: "base_sha256_hash,target_sha256_hash" },
    );
    if (cacheWriteError) throw cacheWriteError;

    // Complete every user job waiting for this exact shared digest pair.
    const { data, error } = await admin.from("app_update_delta_jobs").update({
      ...update,
      expires_at: status === "ready" ? update.expires_at : null,
    }).eq("base_sha256_hash", baseSha).eq("target_sha256_hash", targetSha)
      .in("status", ["queued", "building"]).select("id,status");
    if (error) throw error;
    if (!data || data.length === 0) return json({ error: "jobs_not_found_or_already_finalized" }, 404);
    return json({ jobs_updated: data.length, status });
  } catch (error) {
    console.error("XDelta callback failed", error instanceof Error ? error.message : "unknown_error");
    return json({ error: "callback_failed" }, 500);
  }
});
