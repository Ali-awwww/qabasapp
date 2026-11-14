import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const webhookSecret = Deno.env.get("DEV_CHAT_PUSH_WEBHOOK_SECRET") || "";
const firebaseServiceAccount = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON") || "{}");
const admin = createClient(supabaseUrl, serviceRoleKey);

const base64Url = (bytes: Uint8Array) => {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
};

const encodeJson = (value: unknown) =>
  base64Url(new TextEncoder().encode(JSON.stringify(value)));

async function googleAccessToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = encodeJson({ alg: "RS256", typ: "JWT" });
  const claim = encodeJson({
    iss: firebaseServiceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  });
  const unsigned = `${header}.${claim}`;
  const pem = String(firebaseServiceAccount.private_key || "")
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  const binary = Uint8Array.from(atob(pem), c => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    binary,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  );
  const assertion = `${unsigned}.${base64Url(new Uint8Array(signature))}`;
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!response.ok) throw new Error(`google_oauth_${response.status}`);
  const body = await response.json();
  if (!body.access_token) throw new Error("missing_google_access_token");
  return body.access_token;
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return new Response("method_not_allowed", { status: 405 });
  if (!webhookSecret || request.headers.get("x-qabas-webhook-secret") !== webhookSecret) {
    return new Response("unauthorized", { status: 401 });
  }
  try {
    const payload = await request.json();
    const record = payload.record || payload.new_record || {};
    if (String(record.sender || "").toLowerCase() !== "developer") {
      return Response.json({ skipped: true, reason: "not_developer_reply" });
    }
    const userId = String(record.user_id || "").trim();
    if (!userId) return Response.json({ skipped: true, reason: "missing_user_id" });

    const { data: rows, error } = await admin
      .from("user_push_tokens")
      .select("token")
      .eq("user_id", userId);
    if (error) throw error;
    if (!rows?.length) return Response.json({ sent: 0, reason: "no_registered_devices" });

    const accessToken = await googleAccessToken();
    const projectId = String(firebaseServiceAccount.project_id || "");
    if (!projectId) throw new Error("missing_firebase_project_id");
    const text = String(record.message || "").trim().slice(0, 3500) || "لديك رد جديد من المطور";
    let sent = 0;
    const staleTokens: string[] = [];
    for (const row of rows) {
      const response = await fetch(
        `https://fcm.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/messages:send`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            message: {
              token: row.token,
              android: { priority: "high" },
              data: {
                type: "developer_reply",
                message_id: String(record.id || ""),
                message: text,
              },
            },
          }),
        },
      );
      if (response.ok) sent++;
      else if ([400, 404].includes(response.status)) staleTokens.push(row.token);
    }
    if (staleTokens.length) {
      await admin.from("user_push_tokens").delete().in("token", staleTokens);
    }
    return Response.json({ sent, removed_stale: staleTokens.length });
  } catch (error) {
    console.error(error);
    return Response.json({ error: String(error) }, { status: 500 });
  }
});
