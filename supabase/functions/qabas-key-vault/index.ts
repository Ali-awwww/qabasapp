import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const vaultSecret = Deno.env.get("QABAS_KEY_VAULT_SECRET") ?? "";
const admin = createClient(supabaseUrl, serviceRoleKey);

const allowedServices = new Set([
  "gemini_key", "groq_key", "openai_key", "openrouter_key", "huggingface_key",
  "azure_speech_key", "azure_speech_region", "elevenlabs_key", "pexels_key", "pixabay_key",
]);
const encoder = new TextEncoder();
const decoder = new TextDecoder();

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { "Content-Type": "application/json" },
});

function base64(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function fromBase64(value: string): Uint8Array {
  const binary = atob(value);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

async function vaultKey(): Promise<CryptoKey> {
  if (!vaultSecret || !serviceRoleKey) throw new Error("Key vault is not configured");
  const digest = await crypto.subtle.digest("SHA-256", encoder.encode(vaultSecret));
  return crypto.subtle.importKey(
    "raw",
    digest,
    { name: "AES-GCM" },
    false,
    ["encrypt", "decrypt"],
  );
}

async function encryptValue(value: string): Promise<string> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv },
    await vaultKey(),
    encoder.encode(value),
  );
  return `${base64(iv)}.${base64(new Uint8Array(encrypted))}`;
}

async function decryptValue(payload: string): Promise<string> {
  const [ivEncoded, ciphertextEncoded] = payload.split(".", 2);
  if (!ivEncoded || !ciphertextEncoded) throw new Error("Invalid vault payload");
  const plaintext = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: fromBase64(ivEncoded) },
    await vaultKey(),
    fromBase64(ciphertextEncoded),
  );
  return decoder.decode(plaintext);
}

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
    const payload = await req.json();
    const action = payload?.action;

    if (action === "put") {
      if (!Array.isArray(payload.keys) || payload.keys.length > 20) return json({ error: "invalid_keys" }, 400);
      const rows = [];
      for (const item of payload.keys) {
        const service = String(item?.service ?? "");
        const value = String(item?.value ?? "").trim();
        if (!allowedServices.has(service) || value.length === 0 || value.length > 4096) continue;
        rows.push({
          user_id: user.id,
          service,
          encrypted_value: await encryptValue(value),
          updated_at: new Date().toISOString(),
        });
      }
      if (rows.length === 0) return json({ ok: false, count: 0, error: "no_valid_keys" }, 400);
      const { error } = await admin.from("user_api_keys").upsert(rows, { onConflict: "user_id,service" });
      if (error) throw error;
      return json({ ok: true, count: rows.length });
    }

    if (action === "get") {
      const { data, error } = await admin
        .from("user_api_keys")
        .select("service, encrypted_value, updated_at")
        .eq("user_id", user.id)
        .limit(20);
      if (error) throw error;
      const keys = [];
      for (const row of data ?? []) {
        if (!allowedServices.has(row.service)) continue;
        keys.push({
          service: row.service,
          value: await decryptValue(row.encrypted_value),
          updated_at: row.updated_at,
        });
      }
      return json({ ok: true, keys });
    }

    if (action === "delete") {
      const services = Array.isArray(payload.services)
        ? payload.services.filter((service: unknown) => allowedServices.has(String(service))).map(String)
        : [];
      if (services.length === 0) return json({ ok: false, count: 0 });
      const { error } = await admin
        .from("user_api_keys")
        .delete()
        .eq("user_id", user.id)
        .in("service", services);
      if (error) throw error;
      return json({ ok: true, count: services.length });
    }

    return json({ error: "unsupported_action" }, 400);
  } catch (error) {
    console.error("qabas-key-vault failed", error instanceof Error ? error.message : "unknown");
    return json({ error: "key_vault_error" }, 500);
  }
});
