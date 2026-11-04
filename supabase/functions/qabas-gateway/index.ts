// Qabas secure platform gateway.
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
  "higgsfield",
]);
const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
const jsonHeaders = { ...cors, "Content-Type": "application/json" };

const b64 = (value: Uint8Array) => btoa(String.fromCharCode(...value));
const bytes = (value: string) => Uint8Array.from(atob(value), c => c.charCodeAt(0));

async function keyMaterial() {
  const secret = Deno.env.get("QABAS_KEY_ENCRYPTION_SECRET");
  if (!secret) throw new Error("key_encryption_not_configured");
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(secret));
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
  const { data, error } = await admin.auth.getUser(token);
  if (error) return null;
  return data.user;
}
async function getKeys() {
  const { data, error } = await admin.from("platform_api_keys").select("service,ciphertext,nonce,updated_at");
  if (error) throw error;
  const result: Record<string, string> = {};
  for (const row of data || []) result[row.service] = await decrypt(row.ciphertext, row.nonce);
  return result;
}
const errorResponse = (error: string, status: number) =>
  Response.json({ error }, { status, headers: jsonHeaders });

async function proxyHiggsfield(payload: Record<string, unknown>, apiKey: string): Promise<Response> {
  const operation = String(payload.operation || "");
  const headers = { Authorization: `Key ${apiKey}`, "Content-Type": "application/json", Accept: "application/json" };

  if (operation === "text_to_video") {
    const prompt = typeof payload.prompt === "string" ? payload.prompt.trim() : "";
    const duration = Number(payload.duration);
    const resolution = String(payload.resolution || "1080p");
    const aspectRatio = String(payload.aspect_ratio || "9:16");
    if (!prompt || prompt.length > 8000) return errorResponse("invalid_prompt", 400);
    if (!Number.isInteger(duration) || duration < 3 || duration > 30) return errorResponse("invalid_duration", 400);
    if (!["720p", "1080p"].includes(resolution)) return errorResponse("invalid_resolution", 400);
    if (!["21:9", "16:9", "9:16", "1:1"].includes(aspectRatio)) return errorResponse("invalid_aspect_ratio", 400);

    return await fetch("https://api.higgsfield.ai/kling-video/v4.0/text-to-video", {
      method: "POST",
      headers,
      body: JSON.stringify({ prompt, duration, resolution, aspect_ratio: aspectRatio }),
    }).then(async upstream => new Response(await upstream.text(), {
      status: upstream.status,
      headers: jsonHeaders,
    }));
  }

  if (operation === "image_to_video") {
    const prompt = typeof payload.prompt === "string" ? payload.prompt.trim() : "";
    const imageUrl = typeof payload.image_url === "string" ? payload.image_url.trim() : "";
    const duration = Number(payload.duration);
    const resolution = String(payload.resolution || "1080p");
    if (!prompt || prompt.length > 8000) return errorResponse("invalid_prompt", 400);
    if (!imageUrl.startsWith("https://") || imageUrl.length > 4096) return errorResponse("invalid_image_url", 400);
    if (!Number.isInteger(duration) || duration < 3 || duration > 30) return errorResponse("invalid_duration", 400);
    if (!["720p", "1080p"].includes(resolution)) return errorResponse("invalid_resolution", 400);

    return await fetch("https://api.higgsfield.ai/kling-video/v4.0/image-to-video", {
      method: "POST",
      headers,
      body: JSON.stringify({ image_url: imageUrl, prompt, duration, resolution }),
    }).then(async upstream => new Response(await upstream.text(), {
      status: upstream.status,
      headers: jsonHeaders,
    }));
  }

  if (operation === "status") {
    const requestId = String(payload.request_id || "");
    if (!/^[A-Za-z0-9_-]{1,128}$/.test(requestId)) return errorResponse("invalid_request_id", 400);
    return await fetch(`https://api.higgsfield.ai/requests/${encodeURIComponent(requestId)}/status`, {
      method: "GET",
      headers: { Authorization: `Key ${apiKey}`, Accept: "application/json" },
    }).then(async upstream => new Response(await upstream.text(), {
      status: upstream.status,
      headers: jsonHeaders,
    }));
  }

  if (operation === "upload_image") {
    const contentType = String(payload.content_type || "");
    const encoded = typeof payload.image_base64 === "string" ? payload.image_base64 : "";
    if (!["image/jpeg", "image/png", "image/webp"].includes(contentType)) return errorResponse("invalid_content_type", 400);
    if (!encoded || encoded.length > 16_000_000) return errorResponse("image_payload_too_large_or_empty", 413);

    const imageBytes = bytes(encoded);
    const signedResponse = await fetch("https://api.higgsfield.ai/files/generate-upload-url", {
      method: "POST",
      headers,
      body: JSON.stringify({ content_type: contentType }),
    });
    if (!signedResponse.ok) {
      return new Response(await signedResponse.text(), { status: signedResponse.status, headers: jsonHeaders });
    }

    const signed = await signedResponse.json();
    const uploadUrl = String(signed.upload_url || "");
    const publicUrl = String(signed.public_url || "");
    const uploadHeaders = signed.upload_headers;
    if (!uploadUrl.startsWith("https://") || !publicUrl.startsWith("https://") ||
        !uploadHeaders || typeof uploadHeaders !== "object") {
      return errorResponse("invalid_signed_upload_response", 502);
    }

    const uploadResponse = await fetch(uploadUrl, {
      method: "PUT",
      headers: uploadHeaders,
      body: imageBytes,
    });
    if (!uploadResponse.ok) return errorResponse("image_upload_failed", 502);
    return Response.json({ public_url: publicUrl }, { headers: jsonHeaders });
  }

  return errorResponse("unsupported_higgsfield_operation", 400);
}

Deno.serve(async req => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return errorResponse("method_not_allowed", 405);

  try {
    const user = await identity(req);
    if (!user) return errorResponse("authentication_required", 401);
    const body = await req.json();
    const action = String(body.action || "proxy");
    const isOwner = (user.email || "").toLowerCase() === owner;

    if (action === "save") {
      if (!isOwner) return errorResponse("owner_only", 403);
      const entries = body.keys && typeof body.keys === "object" ? body.keys : {};
      for (const [service, rawValue] of Object.entries(entries)) {
        if (!services.has(service) || typeof rawValue !== "string") continue;
        const value = rawValue.trim();
        if (!value) continue;
        const encrypted = await encrypt(value);
        const { error } = await admin.from("platform_api_keys").upsert({
          service,
          ...encrypted,
          updated_by: user.id,
          updated_at: new Date().toISOString(),
        });
        if (error) throw error;
      }
      return Response.json({ ok: true }, { headers: jsonHeaders });
    }

    if (action === "delete") {
      if (!isOwner) return errorResponse("owner_only", 403);
      const service = String(body.service || "");
      if (!services.has(service)) return errorResponse("unsupported_service", 400);
      const { error } = await admin.from("platform_api_keys").delete().eq("service", service);
      if (error) throw error;
      return Response.json({ ok: true }, { headers: jsonHeaders });
    }

    if (action === "provider_status") {
      const provider = String(body.provider || "");
      if (provider !== "higgsfield") return errorResponse("unsupported_provider", 400);
      const { data, error } = await admin.from("platform_api_keys")
        .select("service")
        .eq("service", provider)
        .maybeSingle();
      if (error) throw error;
      return Response.json({ configured: Boolean(data) }, { headers: jsonHeaders });
    }

    if (action === "status") {
      if (!isOwner) return errorResponse("owner_only", 403);
      const { data, error } = await admin.from("platform_api_keys").select("service,updated_at").order("service");
      if (error) throw error;
      return Response.json({ services: data || [] }, { headers: jsonHeaders });
    }

    if (action !== "proxy") return errorResponse("unknown_action", 400);

    const provider = String(body.provider || "");
    const keys = await getKeys();
    const payload = body.payload && typeof body.payload === "object" ? body.payload as Record<string, unknown> : {};

    if (provider === "higgsfield") {
      if (!keys.higgsfield) return errorResponse("provider_not_configured", 503);
      return await proxyHiggsfield(payload, keys.higgsfield);
    }

    let url = "";
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    if (provider === "gemini" && keys.gemini) {
      url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${keys.gemini}`;
    } else if (provider === "groq" && keys.groq) {
      url = "https://api.groq.com/openai/v1/chat/completions";
      headers.Authorization = `Bearer ${keys.groq}`;
    } else if (provider === "openai" && keys.openai) {
      url = "https://api.openai.com/v1/chat/completions";
      headers.Authorization = `Bearer ${keys.openai}`;
    } else if (provider === "openrouter" && keys.openrouter) {
      url = "https://openrouter.ai/api/v1/chat/completions";
      headers.Authorization = `Bearer ${keys.openrouter}`;
    } else {
      return errorResponse("provider_not_configured", 503);
    }

    const upstream = await fetch(url, { method: "POST", headers, body: JSON.stringify(payload) });
    return new Response(await upstream.text(), { status: upstream.status, headers: jsonHeaders });
  } catch (error) {
    console.error("Qabas gateway request failed", error instanceof Error ? error.message : "unknown_error");
    return errorResponse("gateway_error", 500);
  }
});