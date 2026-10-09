// Qabas secure platform gateway.
// Deploy with: supabase functions deploy qabas-gateway
// Required secrets: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY,
// QABAS_OWNER_EMAIL, QABAS_KEY_ENCRYPTION_SECRET.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const owner = (Deno.env.get("QABAS_OWNER_EMAIL") || "aliwalead.2007@gmail.com").toLowerCase();
const services = new Set([
  "gemini", "groq", "openai", "openrouter", "huggingface", "elevenlabs",
  "azure_speech", "pexels", "pixabay", "coverr", "qf_client_id", "qf_client_secret",
]);
const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

const b64 = (bytes: Uint8Array) => btoa(String.fromCharCode(...bytes));
const bytes = (value: string) => Uint8Array.from(atob(value), c => c.charCodeAt(0));
async function keyMaterial() {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(Deno.env.get("QABAS_KEY_ENCRYPTION_SECRET")!));
  return crypto.subtle.importKey("raw", digest, "AES-GCM", false, ["encrypt", "decrypt"]);
}
async function encrypt(value: string) {
  const nonce = crypto.getRandomValues(new Uint8Array(12));
  const cipher = await crypto.subtle.encrypt({ name: "AES-GCM", iv: nonce }, await keyMaterial(), new TextEncoder().encode(value));
  return { ciphertext: b64(new Uint8Array(cipher)), nonce: b64(nonce) };
}
async function decrypt(ciphertext: string, nonce: string) {
  const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: bytes(nonce) }, await keyMaterial(), bytes(ciphertext));
  return new TextDecoder().decode(plain);
}
async function identity(req: Request) {
  const token = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
  if (!token) return null;
  const { data } = await admin.auth.getUser(token);
  return data.user;
}
async function getKeys() {
  const { data, error } = await admin.from("platform_api_keys").select("service,ciphertext,nonce,updated_at");
  if (error) throw error;
  const result: Record<string, string> = {};
  for (const row of data || []) result[row.service] = await decrypt(row.ciphertext, row.nonce);
  return result;
}

Deno.serve(async req => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  try {
    const user = await identity(req);
    if (!user) return Response.json({ error: "authentication_required" }, { status: 401, headers: cors });
    const body = await req.json();
    const action = body.action || "proxy";
    const isOwner = (user.email || "").toLowerCase() === owner;

    if (action === "save") {
      if (!isOwner) return Response.json({ error: "owner_only" }, { status: 403, headers: cors });
      const entries = body.keys || {};
      for (const [service, value] of Object.entries(entries)) {
        if (!services.has(service) || typeof value !== "string") continue;
        const encrypted = await encrypt(value.trim());
        const { error } = await admin.from("platform_api_keys").upsert({ service, ...encrypted, updated_by: user.id, updated_at: new Date().toISOString() });
        if (error) throw error;
      }
      return Response.json({ ok: true }, { headers: cors });
    }
    if (action === "status") {
      if (!isOwner) return Response.json({ error: "owner_only" }, { status: 403, headers: cors });
      const { data, error } = await admin.from("platform_api_keys").select("service,updated_at").order("service");
      if (error) throw error;
      return Response.json({ services: data || [] }, { headers: cors });
    }
    if (action !== "proxy") return Response.json({ error: "unknown_action" }, { status: 400, headers: cors });

    const provider = String(body.provider || "");
    const keys = await getKeys();
    const payload = body.payload || {};
    let url = "";
    let headers: Record<string, string> = { "Content-Type": "application/json" };
    if (provider === "gemini" && keys.gemini) { url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${keys.gemini}`; }
    else if (provider === "groq" && keys.groq) { url = "https://api.groq.com/openai/v1/chat/completions"; headers.Authorization = `Bearer ${keys.groq}`; }
    else if (provider === "openai" && keys.openai) { url = "https://api.openai.com/v1/chat/completions"; headers.Authorization = `Bearer ${keys.openai}`; }
    else if (provider === "openrouter" && keys.openrouter) { url = "https://openrouter.ai/api/v1/chat/completions"; headers.Authorization = `Bearer ${keys.openrouter}`; }
    else return Response.json({ error: "provider_not_configured" }, { status: 503, headers: cors });
    const upstream = await fetch(url, { method: "POST", headers, body: JSON.stringify(payload) });
    return new Response(await upstream.text(), { status: upstream.status, headers: { ...cors, "Content-Type": "application/json" } });
  } catch (error) {
    console.error(error);
    return Response.json({ error: "gateway_error" }, { status: 500, headers: cors });
  }
});
