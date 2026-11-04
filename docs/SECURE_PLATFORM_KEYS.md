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


## Higgsfield Kling 4.0

The Android app does not store or send the Higgsfield credential to the provider. The developer-only Studio key dialog saves the complete copied API key through the authenticated gateway into the encrypted `platform_api_keys` vault. The gateway allowlists these operations:

- `text_to_video`: POST to `/kling-video/v4.0/text-to-video`.
- `image_to_video`: POST to `/kling-video/v4.0/image-to-video`.
- `upload_image`: requests a signed upload URL, uploads the image, and returns only its public URL.
- `status`: polls `/requests/{request_id}/status`.

The gateway returns only the provider response and never returns the stored credential. The app treats a saved key as configured, not as provider-validated; validation occurs only when a real generation request is submitted. The image upload operation accepts JPEG, PNG, or WebP base64 payloads up to the gateway's configured size limit.

After updating the function source, deploy it with JWT verification disabled because the handler validates the Supabase user session itself:

```bash
supabase functions deploy qabas-gateway
```
