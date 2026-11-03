// Authenticate by Qabas username or account number without exposing profile email lookup.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;

const admin = createClient(supabaseUrl, serviceRoleKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});
const authClient = createClient(supabaseUrl, anonKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...cors, "Content-Type": "application/json; charset=utf-8" },
  });
}

async function resolveEmail(identifier: string): Promise<string | null> {
  const clean = identifier.trim();
  if (!clean || clean.length > 64) return null;

  // Usernames are resolved only inside this server-side function.
  const handle = clean.replace(/^@/, "").toLowerCase();
  const byHandle = await admin
    .from("users")
    .select("email")
    .eq("custom_id", handle)
    .limit(2);

  if (!byHandle.error && byHandle.data?.length === 1) {
    const email = byHandle.data[0].email;
    return typeof email === "string" && email.includes("@") ? email : null;
  }
  if (!byHandle.error && (byHandle.data?.length ?? 0) > 1) return null;

  // Accept #QBS-0012, QBS-0012, or 0012 as the account number.
  const digits = clean.replace(/\D/g, "");
  if (!digits) return null;
  const normalized = digits.padStart(4, "0");
  const numberForms = [...new Set([
    clean,
    normalized,
    "QBS-" + normalized,
    "#QBS-" + normalized,
  ])];

  const byAccount = await admin
    .from("users")
    .select("email")
    .in("account_number", numberForms)
    .limit(2);

  if (byAccount.error || byAccount.data?.length !== 1) return null;
  const email = byAccount.data[0].email;
  return typeof email === "string" && email.includes("@") ? email : null;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "invalid_credentials" }, 405);

  try {
    const body = await req.json();
    const identifier = typeof body.identifier === "string" ? body.identifier : "";
    const password = typeof body.password === "string" ? body.password : "";
    if (!identifier.trim() || password.length < 6 || password.length > 1024) {
      return json({ error: "invalid_credentials" }, 401);
    }

    const email = await resolveEmail(identifier);
    if (!email) return json({ error: "invalid_credentials" }, 401);

    // Supabase Auth validates the password and applies its normal rate limits.
    // Only Auth's own session payload is returned; the profile lookup email
    // is never returned as a standalone field.
    const { data, error } = await authClient.auth.signInWithPassword({
      email,
      password,
    });
    if (error || !data.session) return json({ error: "invalid_credentials" }, 401);

    return json({
      access_token: data.session.access_token,
      refresh_token: data.session.refresh_token,
      expires_in: data.session.expires_in,
      token_type: data.session.token_type,
    });
  } catch (error) {
    console.error("Identifier sign-in failed:", error instanceof Error ? error.message : "unknown");
    return json({ error: "authentication_unavailable" }, 503);
  }
});
