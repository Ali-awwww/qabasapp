// Owner-only GitHub token management. The PAT is never returned to the client.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const admin = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { autoRefreshToken: false, persistSession: false } },
);
const ownerEmail = (Deno.env.get("QABAS_OWNER_EMAIL") || "admin@qabas.studio").trim().toLowerCase();
const json = (body: unknown, status = 200) => Response.json(body, {
  status,
  headers: { ...cors, "Content-Type": "application/json; charset=utf-8" },
});

async function isOwner(req: Request): Promise<boolean> {
  const bearer = (req.headers.get("Authorization") || "").match(/^Bearer\s+(.+)$/i)?.[1];
  if (!bearer) return false;
  const { data, error } = await admin.auth.getUser(bearer);
  if (error || !data.user) return false;
  return (data.user.email || "").trim().toLowerCase() === ownerEmail;
}

async function validateGitHubToken(token: string): Promise<boolean> {
  const headers = {
    Accept: "application/vnd.github+json",
    Authorization: `Bearer ${token}`,
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "Qabas-Xdelta-Token-Manager",
  };
  const workflowResponse = await fetch(
    "https://api.github.com/repos/Ali-awwww/qabasapp/actions/workflows/xdelta--updates.yml",
    { headers },
  );
  return workflowResponse.ok;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  try {
    if (!await isOwner(req)) return json({ error: "owner_only" }, 403);

    const body = await req.json().catch(() => null);
    if (!body || typeof body !== "object") return json({ error: "invalid_request" }, 400);
    const action = (body as Record<string, unknown>).action;

    if (action === "status") {
      const { data, error } = await admin.from("app_secrets")
        .select("id")
        .eq("secret_key", "github_token")
        .eq("is_active", true)
        .limit(1);
      if (error) throw error;
      return json({ configured: (data?.length ?? 0) > 0 });
    }

    if (action !== "save") return json({ error: "invalid_action" }, 400);
    const token = (body as Record<string, unknown>).token;
    if (typeof token !== "string" || token.trim().length < 20 || token.trim().length > 512) {
      return json({ error: "invalid_token_format" }, 400);
    }

    const cleanToken = token.trim();
    if (!await validateGitHubToken(cleanToken)) {
      return json({ error: "github_token_invalid_or_missing_repository_access" }, 422);
    }

    const { error } = await admin.rpc("replace_github_token", { p_secret_value: cleanToken });
    if (error) {
      console.error("GitHub token save failed", error.code || "database_error");
      return json({ error: "token_save_failed" }, 500);
    }

    return json({ configured: true });
  } catch (error) {
    console.error("GitHub token management failed", error instanceof Error ? error.message : "unknown_error");
    return json({ error: "request_failed" }, 500);
  }
});
