const PAGE = `<!doctype html>
<html lang="pt-PT">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="referrer" content="no-referrer"><meta name="robots" content="noindex,nofollow">
<title>StudioMesa — eliminação de conta</title>
<style>
:root{color-scheme:light;--green:#304a3b;--paper:#f7f7f1;--ink:#25332a;--muted:#657168;--border:#d9ddd5;--danger:#8f322b}
*{box-sizing:border-box}body{margin:0;background:var(--paper);color:var(--ink);font:16px/1.55 system-ui,-apple-system,Segoe UI,sans-serif}
main{width:min(100% - 32px,640px);margin:32px auto}header{background:var(--green);color:white;padding:24px;border-radius:18px}
h1{margin:0 0 8px;font-size:clamp(1.6rem,5vw,2.2rem);line-height:1.2}header p{margin:0;opacity:.92}
section{margin-top:18px;background:white;border:1px solid var(--border);border-radius:16px;padding:22px}
h2{font-size:1.2rem;margin:0 0 10px}p{margin:8px 0 14px}label{display:block;margin:14px 0 6px;font-weight:600}
input[type=email]{display:block;width:100%;padding:13px;border:1px solid #8b978e;border-radius:9px;font:inherit}
.option{display:flex;gap:10px;align-items:flex-start;padding:12px;border:1px solid var(--border);border-radius:10px;font-weight:400}
.option input,.confirm input{margin-top:6px}.confirm{display:flex;gap:10px;align-items:flex-start;font-weight:400}
button{width:100%;margin-top:16px;padding:13px 16px;border:0;border-radius:10px;background:var(--green);color:white;font:inherit;font-weight:700;cursor:pointer}
button:disabled{opacity:.55;cursor:wait}button.danger{background:var(--danger)}[hidden]{display:none!important}
#status{padding:12px;background:#eef3ee;border-radius:10px;overflow-wrap:anywhere}#status.error{background:#fff0ed;color:#79271f}
.small{font-size:.9rem;color:var(--muted)}a{color:var(--green)}
</style>
</head>
<body><main>
<header><h1>StudioMesa</h1><p>Pedido de eliminação de conta e dados associados</p></header>
<section><h2>1. Confirmar que controlas a conta</h2>
<p>Introduz o email da conta StudioMesa. Enviaremos uma ligação de autenticação única. Abre-a no mesmo navegador para continuar. Por segurança, esta página não confirma se um email está registado.</p>
<form id="email-form"><label for="email">Email da conta</label><input id="email" name="email" type="email" autocomplete="email" required maxlength="254"><button id="send-link" type="submit">Enviar ligação segura</button></form>
</section>
<section id="signed-in" hidden><h2>2. Escolher o âmbito da eliminação</h2><p id="account-label"></p>
<form id="delete-form">
<label class="option"><input type="radio" name="deletion_scope" value="account" checked><span><strong>Eliminar apenas a minha conta</strong><br>Remove a conta e a associação de fidelização. Os registos operacionais partilhados que tenham de ser preservados podem manter-se, mas a referência ao autor será anonimizada.</span></label>
<label class="option"><input type="radio" name="deletion_scope" value="restaurant"><span><strong>Eliminar a minha conta e todo o restaurante</strong><br>Elimina permanentemente a carta, mesas, pedidos, reservas, inventário, códigos e dados de fidelização associados. Só disponível para o proprietário quando já não existirem outros membros da equipa.</span></label>
<label class="confirm"><input id="confirm-delete" type="checkbox" required><span>Compreendo que a eliminação é permanente e não pode ser desfeita.</span></label>
<button id="delete-button" class="danger" type="submit">Eliminar definitivamente</button></form>
<p class="small">Se fores proprietário e ainda existirem membros da equipa, o servidor bloqueará a eliminação do restaurante. Remove primeiro esses membros na app e volta a tentar.</p>
</section>
<section><p id="status" role="status" aria-live="polite">Nenhuma eliminação foi solicitada.</p><p class="small">A tua conta só é eliminada depois de confirmares a ação e o servidor validar a sessão e as permissões. Não introduzas aqui a palavra-passe; usa apenas a ligação enviada ao teu email.</p></section>
<footer style="margin:22px 4px;font-size:.9rem;color:#657168">StudioMesa · Gestão de restaurante. A política de privacidade pública tem de estar disponível antes da publicação comercial.</footer>
</main>
<script nonce="__STUDIOMESA_CSP_NONCE__" type="module">
import { createClient } from "https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.118.0/+esm";
const projectUrl = "https://svwvkfsyevuyabelrtjk.supabase.co";
const publishableKey = "sb_publishable_ioJIukpwd0_Fyajd41QL_Q_M6WKE0UH";
const supabase = createClient(projectUrl,publishableKey,{auth:{persistSession:true,autoRefreshToken:true,detectSessionInUrl:true,flowType:"pkce"}});
const emailForm=document.querySelector("#email-form"),sendButton=document.querySelector("#send-link"),signedInPanel=document.querySelector("#signed-in"),accountLabel=document.querySelector("#account-label"),deleteForm=document.querySelector("#delete-form"),deleteButton=document.querySelector("#delete-button"),statusEl=document.querySelector("#status");
let currentSession=null,deletionSubmitted=false;
function showStatus(message,error=false){statusEl.textContent=message;statusEl.classList.toggle("error",error)}
function showSession(session){currentSession=session;if(!session?.access_token||!session?.user?.email||deletionSubmitted)return;emailForm.hidden=true;signedInPanel.hidden=false;accountLabel.textContent="Sessão autenticada para "+session.user.email+".";showStatus("Sessão confirmada. Revê cuidadosamente o âmbito antes de continuares")}
const redirectUrl=window.location.origin+window.location.pathname;
emailForm.addEventListener("submit",async event=>{
 event.preventDefault();sendButton.disabled=true;showStatus("A pedir uma ligação segura…");
 try{const email=document.querySelector("#email").value.trim();const {error}=await supabase.auth.signInWithOtp({email,options:{shouldCreateUser:false,emailRedirectTo:redirectUrl}});if(error)throw error;showStatus("Se a conta puder iniciar sessão por este método, receberás uma ligação no email indicado. Abre-a no mesmo navegador para continuar.")}
 catch{showStatus("Não foi possível enviar a ligação. Confirma o endereço e tenta mais tarde. A configuração de redirecionamentos de autenticação pode precisar de atenção.",true)}
 finally{sendButton.disabled=false}
});
deleteForm.addEventListener("submit",async event=>{
 event.preventDefault();
 if(!currentSession?.access_token||deletionSubmitted){showStatus("A sessão não está confirmada. Solicita uma nova ligação de autenticação.",true);return}
 if(!document.querySelector("#confirm-delete").checked){showStatus("Marca a confirmação antes de continuar.",true);return}
 const deleteRestaurantData=document.querySelector('input[name="deletion_scope"]:checked')?.value==="restaurant";
 if(!window.confirm(deleteRestaurantData?"Confirma a eliminação permanente da tua conta E de todos os dados do restaurante?":"Confirma a eliminação permanente da tua conta StudioMesa?"))return;
 deleteButton.disabled=true;showStatus("A validar a sessão e a processar o pedido…");
 try{
  const response=await fetch(projectUrl+"/functions/v1/delete-account",{method:"POST",headers:{"Content-Type":"application/json","apikey":publishableKey,"Authorization":"Bearer "+currentSession.access_token},body:JSON.stringify({delete_restaurant_data:deleteRestaurantData})});
  const result=await response.json().catch(()=>({}));
  if(!response.ok)throw new Error(typeof result.message==="string"?result.message:"O servidor não autorizou a eliminação.");
  deletionSubmitted=true;deleteForm.hidden=true;signedInPanel.hidden=true;emailForm.hidden=true;
  showStatus(result.restaurant_data_deleted?"A conta e os dados associados ao restaurante foram eliminados.":"A conta foi eliminada. Os dados operacionais partilhados preservados tiveram a autoria anonimizada.");
  await supabase.auth.signOut({scope:"local"}).catch(()=>{});
 }catch(error){showStatus(error instanceof Error?error.message:"Não foi possível confirmar a eliminação. Verifica o estado da conta antes de repetir.",true)}
 finally{deleteButton.disabled=false}
});
supabase.auth.onAuthStateChange((_event,session)=>{if(session)showSession(session)});
const {data}=await supabase.auth.getSession();if(data.session)showSession(data.session);
</script>
</body></html>`;

Deno.serve((req: Request) => {
 if (req.method !== "GET") {
  return Response.json({ message: "Método não permitido." }, {
   status: 405,
   headers: {
    "Cache-Control": "no-store, max-age=0",
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer",
    "X-Frame-Options": "DENY",
   },
  });
 }

 const nonceBytes = crypto.getRandomValues(new Uint8Array(18));
 const nonce = btoa(String.fromCharCode(...nonceBytes));
 const page = PAGE.replace("__STUDIOMESA_CSP_NONCE__", nonce);
 const contentSecurityPolicy = [
  "default-src 'none'",
  "base-uri 'none'",
  "connect-src https://svwvkfsyevuyabelrtjk.supabase.co",
  "form-action 'self'",
  "frame-ancestors 'none'",
  "img-src 'self' data:",
  "object-src 'none'",
  `script-src 'nonce-${nonce}' https://cdn.jsdelivr.net`,
  "style-src 'unsafe-inline'",
 ].join("; ");

 return new Response(page, {
  status: 200,
  headers: {
   "Content-Type": "text/html; charset=utf-8",
   "Cache-Control": "no-store, max-age=0",
   "Content-Security-Policy": contentSecurityPolicy,
   "X-Content-Type-Options": "nosniff",
   "Referrer-Policy": "no-referrer",
   "X-Frame-Options": "DENY",
   "Permissions-Policy": "camera=(), microphone=(), geolocation=()",
  },
 });
});
