// Minimal globals needed to type-check Supabase Edge Functions without bundling Deno.
// The hosted Supabase runtime provides the real declarations at deployment time.
declare module "jsr:@supabase/functions-js/edge-runtime.d.ts" {}

declare const Deno: {
  env: {
    get(name: string): string | undefined;
  };
  serve(handler: (request: Request) => Response | Promise<Response>): void;
};
