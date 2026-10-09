# Secure platform API gateway

The developer API Keys screen is the control plane, but provider secrets must not be downloaded to Android devices. Deploy the gateway and migration before enabling central keys.

## Supabase setup

1. Apply `supabase/migrations/202609220001_platform_api_keys.sql`.
2. Deploy `supabase/functions/qabas-gateway`.
3. Set function secrets:

```bash
supabase secrets set QABAS_OWNER_EMAIL=aliwalead.2007@gmail.com
supabase secrets set QABAS_KEY_ENCRYPTION_SECRET="use-a-long-random-value"
supabase functions deploy qabas-gateway
```

`SUPABASE_SERVICE_ROLE_KEY` is supplied by Supabase to Edge Functions and must never be placed in the APK.

The gateway supports authenticated `save`, `status`, and provider `proxy` requests. The Android UI must call `save` for the owner and use `proxy` for shared providers; it must not pull platform secrets into `SharedPreferences`.
