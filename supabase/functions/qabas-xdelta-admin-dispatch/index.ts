// Owner-only dispatch for the developer's release-to-release XDelta workflow.
// GitHub PAT is read exclusively from server-side app_secrets.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { autoRefreshToken: false, persistSession: false },
});
const ownerEmail = (Deno.env.get("QABAS_OWNER_EMAIL") || "admin@qabas.studio").trim().toLowerCase();
const json = (body: unknown, status = 200) => Response.json(body, {
  status, headers: { ...cors, "Content-Type": "application/json; charset=utf-8" },
});
const validTag = (value: unknown): value is string =>
  typeof value === "string" && /^[A-Za-z0-9._+-]{1,128}$/.test(value) && value !== "." && value !== "..";

async function getApprovedReleaseAsset(token: string, tag: string): Promise<boolean> {
  const response = await fetch(
    `https://api.github.com/repos/Ali-awwww/qabasapp/releases/tags/${encodeURIComponent(tag)}`,
    {
      headers: {
        Accept: "application/vnd.github+json",
        Authorization: `Bearer ${token}`,
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "Qabas-Xdelta-Admin-Dispatch",
      },
    },
  );
  if (!response.ok) return false;
  const release = await response.json();
  const assets = Array.isArray(release.assets) ? release.assets : [];
  return assets.some((asset: Record<string, unknown>) =>
    typeof asset.name === "string" && asset.name.toLowerCase().endsWith(".apk") &&
    typeof asset.browser_download_url === "string" &&
    asset.browser_download_url.startsWith("https://github.com/Ali-awwww/qabasapp/releases/download/")
  );
}

Deno.serve(async req => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  try {
    const bearer = (req.headers.get("Authorization") || "").match(/^Bearer\s+(.+)$/i)?.[1];
    if (!bearer) return json({ error: "authentication_required" }, 401);
    const { data: auth, error: authError } = await admin.auth.getUser(bearer);
    if (authError || !auth.user) return json({ error: "authentication_required" }, 401);
    if ((auth.user.email || "").toLowerCase() !== ownerEmail) return json({ error: "owner_only" }, 403);

    const body = await req.json();
    const targetTag = body.target_tag;
    const previousTag = body.previous_tag;
    if (!validTag(targetTag) || !validTag(previousTag) || targetTag === previousTag) {
      return json({ error: "invalid_release_tags" }, 400);
    }

    const { data: secret, error: secretError } = await admin.from("app_secrets")
      .select("secret_value").eq("secret_key", "github_token").eq("is_active", true)
      .limit(1).maybeSingle();
    if (secretError) throw secretError;
    const githubToken = typeof secret?.secret_value === "string" ? secret.secret_value.trim() : "";
    if (!githubToken) return json({ error: "server_dispatch_not_configured" }, 503);

    const [targetExists, previousExists] = await Promise.all([
      getApprovedReleaseAsset(githubToken, targetTag),
      getApprovedReleaseAsset(githubToken, previousTag),
    ]);
    if (!targetExists || !previousExists) return json({ error: "release_apk_asset_not_found" }, 409);

    const dispatch = await fetch("https://api.github.com/repos/Ali-awwww/qabasapp/actions/workflows/xdelta--updates.yml/dispatches", {
      method: "POST",
      headers: {
        Accept: "application/vnd.github+json",
        Authorization: `Bearer ${githubToken}`,
        "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/json",
        "User-Agent": "Qabas-Xdelta-Admin-Dispatch",
      },
      body: JSON.stringify({
        ref: "main",
        inputs: { target_tag: targetTag, previous_tag: previousTag },
      }),
    });
    if (dispatch.status !== 204) return json({ error: "dispatch_failed" }, 502);
    return json({ accepted: true, target_tag: targetTag, previous_tag: previousTag }, 202);
  } catch (error) {
    console.error("XDelta admin dispatch failed", error instanceof Error ? error.message : "unknown_error");
    return json({ error: "request_failed" }, 500);
  }
});
