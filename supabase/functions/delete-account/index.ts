import { withSupabase } from "npm:@supabase/server@1";
import { isDeletionConflictMessage } from "./error-classification.ts";

/**
 * Deletes only the account represented by the verified user session.
 *
 * The database RPC stages a short-lived deletion request. When Auth actually
 * deletes the user, a database trigger consumes that request and, for an owner
 * who explicitly confirmed it, deletes the now-unshared restaurant data in the
 * same database transaction. The service-role/secret key is never returned to
 * the client and is handled by ctx.supabaseAdmin.
 */
Deno.serve(
  withSupabase({ auth: "user" }, async (req, ctx) => {
    if (req.method !== "POST") {
      return Response.json({ message: "Método não permitido." }, { status: 405 });
    }

    let body: { delete_restaurant_data?: unknown };
    try {
      body = await req.json();
    } catch {
      return Response.json({ message: "O pedido não contém JSON válido." }, { status: 400 });
    }

    if (typeof body.delete_restaurant_data !== "boolean") {
      return Response.json(
        { message: "Confirma explicitamente se os dados de um restaurante devem ser eliminados." },
        { status: 400 },
      );
    }

    const userId = ctx.userClaims?.id;
    if (!userId || typeof userId !== "string") {
      return Response.json({ message: "A sessão não identifica uma conta válida. Inicia sessão novamente." }, { status: 401 });
    }

    const { data: preparation, error: preparationError } = await ctx.supabase.rpc(
      "prepare_account_deletion",
      { p_delete_restaurant: body.delete_restaurant_data },
    );

    if (preparationError) {
      const message = preparationError.message ?? "";
      if (isDeletionConflictMessage(message)) {
        return Response.json({ message }, { status: 409 });
      }
      return Response.json(
        { message: "Não foi possível preparar a eliminação da conta. Verifica o estado da conta e tenta novamente." },
        { status: 400 },
      );
    }

    try {
      const { error: deletionError } = await ctx.supabaseAdmin.auth.admin.deleteUser(userId);
      if (deletionError) {
        // If Auth rejects deletion, clear the short-lived staged request when
        // the user's session is still valid. The database expiry is a fallback.
        await ctx.supabase.rpc("cancel_account_deletion");

        const deletionMessage = deletionError.message ?? "";
        if (deletionMessage.includes("A equipa do restaurante mudou durante o pedido")) {
          return Response.json(
            { message: "A equipa do restaurante mudou enquanto processávamos o pedido. A conta não foi eliminada. Atualiza a equipa e volta a confirmar a eliminação." },
            { status: 409 },
          );
        }

        return Response.json(
          { message: "Não foi possível confirmar a eliminação da conta. Inicia sessão e verifica o estado antes de tentares novamente." },
          { status: 502 },
        );
      }
    } catch {
      // A timeout can occur after the server has already processed deletion.
      // Never claim that nothing changed; the short-lived queue expires safely.
      return Response.json(
        { message: "Não foi possível confirmar a resposta do servidor. Verifica se a conta ainda permite iniciar sessão antes de repetires o pedido." },
        { status: 502 },
      );
    }

    return Response.json({
      deleted: true,
      restaurant_data_deleted: preparation?.restaurant_data_will_be_deleted === true,
    });
  }),
);
