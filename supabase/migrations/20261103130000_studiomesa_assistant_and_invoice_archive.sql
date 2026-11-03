-- Owner-only StudioMesa assistant conversations and private fiscal invoice archive.
-- Invoice files are stored in a private Storage bucket. The AI receives a PDF only
-- when the owner explicitly attaches it to a chat request.
BEGIN;

CREATE TABLE IF NOT EXISTS private.studiomesa_assistant_conversations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    title text NOT NULL DEFAULT 'Nova conversa'
        CHECK (char_length(trim(title)) BETWEEN 1 AND 120),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT studiomesa_assistant_conversation_scope_unique UNIQUE (id, restaurant_id, user_id)
);
CREATE INDEX IF NOT EXISTS studiomesa_assistant_conversations_recent_idx
    ON private.studiomesa_assistant_conversations (restaurant_id, user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS private.studiomesa_assistant_messages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid NOT NULL,
    restaurant_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role text NOT NULL CHECK (role IN ('user', 'assistant')),
    content text NOT NULL CHECK (char_length(trim(content)) BETWEEN 1 AND 12000),
    citations jsonb NOT NULL DEFAULT '[]'::jsonb
        CHECK (jsonb_typeof(citations) = 'array'),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT studiomesa_assistant_message_conversation_fk
        FOREIGN KEY (conversation_id, restaurant_id, user_id)
        REFERENCES private.studiomesa_assistant_conversations (id, restaurant_id, user_id)
        ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS studiomesa_assistant_messages_conversation_idx
    ON private.studiomesa_assistant_messages (conversation_id, created_at, id);
CREATE INDEX IF NOT EXISTS studiomesa_assistant_messages_search_idx
    ON private.studiomesa_assistant_messages (restaurant_id, user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS private.studiomesa_financial_invoices (
    id uuid PRIMARY KEY,
    restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
    uploaded_by uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    storage_path text NOT NULL UNIQUE,
    file_name text NOT NULL CHECK (char_length(trim(file_name)) BETWEEN 1 AND 180),
    mime_type text NOT NULL CHECK (mime_type = 'application/pdf'),
    file_size_bytes integer NOT NULL CHECK (file_size_bytes BETWEEN 1 AND 10485760),
    upload_state text NOT NULL DEFAULT 'pending' CHECK (upload_state IN ('pending', 'uploaded')),
    created_at timestamptz NOT NULL DEFAULT now(),
    uploaded_at timestamptz,
    CONSTRAINT studiomesa_financial_invoice_state_check
        CHECK ((upload_state = 'pending' AND uploaded_at IS NULL)
            OR (upload_state = 'uploaded' AND uploaded_at IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS studiomesa_financial_invoices_recent_idx
    ON private.studiomesa_financial_invoices (restaurant_id, created_at DESC);

ALTER TABLE private.studiomesa_assistant_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE private.studiomesa_assistant_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE private.studiomesa_financial_invoices ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE private.studiomesa_assistant_conversations FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE private.studiomesa_assistant_messages FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE private.studiomesa_financial_invoices FROM PUBLIC, anon, authenticated;
GRANT USAGE ON SCHEMA private TO authenticated;

CREATE OR REPLACE FUNCTION private.studiomesa_assistant_owner_restaurant_id()
RETURNS uuid
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_user_id uuid := (SELECT auth.uid());
    v_restaurant_id uuid;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;

    SELECT rs.restaurant_id
      INTO v_restaurant_id
      FROM public.restaurant_staff AS rs
     WHERE rs.user_id = v_user_id
       AND rs.role = 'owner'
     ORDER BY rs.created_at ASC
     LIMIT 1;

    IF v_restaurant_id IS NULL THEN
        RAISE EXCEPTION 'O Assistente StudioMesa e o arquivo financeiro estão reservados ao proprietário do restaurante.';
    END IF;
    RETURN v_restaurant_id;
END;
$function$;
REVOKE ALL ON FUNCTION private.studiomesa_assistant_owner_restaurant_id() FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION private.studiomesa_assistant_list_conversations_internal(p_query text DEFAULT NULL)
RETURNS TABLE (
    conversation_id uuid,
    title text,
    created_at timestamptz,
    updated_at timestamptz,
    last_message_preview text
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_user_id uuid := (SELECT auth.uid());
    v_restaurant_id uuid;
    v_query text := trim(coalesce(p_query, ''));
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;
    IF char_length(v_query) > 100 THEN
        RAISE EXCEPTION 'A pesquisa do histórico pode ter no máximo 100 caracteres.';
    END IF;
    v_restaurant_id := private.studiomesa_assistant_owner_restaurant_id();

    RETURN QUERY
    SELECT c.id, c.title, c.created_at, c.updated_at,
           coalesce((
               SELECT left(regexp_replace(m.content, '[[:space:]]+', ' ', 'g'), 180)
                 FROM private.studiomesa_assistant_messages AS m
                WHERE m.conversation_id = c.id
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT 1
           ), '')
      FROM private.studiomesa_assistant_conversations AS c
     WHERE c.restaurant_id = v_restaurant_id
       AND c.user_id = v_user_id
       AND (
           v_query = ''
           OR c.title ILIKE '%' || v_query || '%'
           OR EXISTS (
               SELECT 1
                 FROM private.studiomesa_assistant_messages AS m
                WHERE m.conversation_id = c.id
                  AND m.content ILIKE '%' || v_query || '%'
           )
       )
     ORDER BY c.updated_at DESC, c.id DESC
     LIMIT 50;
END;
$function$;
REVOKE ALL ON FUNCTION private.studiomesa_assistant_list_conversations_internal(text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.studiomesa_assistant_list_conversations_internal(text) TO authenticated;

CREATE OR REPLACE FUNCTION private.studiomesa_assistant_create_conversation_internal(p_title text DEFAULT NULL)
RETURNS TABLE (conversation_id uuid, title text, created_at timestamptz)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_user_id uuid := (SELECT auth.uid());
    v_restaurant_id uuid;
    v_title text := left(trim(regexp_replace(coalesce(p_title, ''), '[[:space:]]+', ' ', 'g')), 120);
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;
    IF v_title = '' THEN v_title := 'Nova conversa'; END IF;
    v_restaurant_id := private.studiomesa_assistant_owner_restaurant_id();

    RETURN QUERY
    INSERT INTO private.studiomesa_assistant_conversations AS c
        (restaurant_id, user_id, title)
    VALUES (v_restaurant_id, v_user_id, v_title)
    RETURNING c.id, c.title, c.created_at;
END;
$function$;
REVOKE ALL ON FUNCTION private.studiomesa_assistant_create_conversation_internal(text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.studiomesa_assistant_create_conversation_internal(text) TO authenticated;

CREATE OR REPLACE FUNCTION private.studiomesa_assistant_list_messages_internal(p_conversation_id uuid)
RETURNS TABLE (message_id uuid, role text, content text, citations jsonb, created_at timestamptz)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_user_id uuid := (SELECT auth.uid());
    v_restaurant_id uuid;
BEGIN
    IF v_user_id IS NULL OR p_conversation_id IS NULL THEN
        RAISE EXCEPTION 'É necessário identificar a conversa e iniciar sessão.';
    END IF;
    v_restaurant_id := private.studiomesa_assistant_owner_restaurant_id();
    IF NOT EXISTS (
        SELECT 1
          FROM private.studiomesa_assistant_conversations AS c
         WHERE c.id = p_conversation_id
           AND c.restaurant_id = v_restaurant_id
           AND c.user_id = v_user_id
    ) THEN
        RAISE EXCEPTION 'Esta conversa não pertence a esta conta.';
    END IF;

    RETURN QUERY
    SELECT m.id, m.role, m.content, m.citations, m.created_at
      FROM private.studiomesa_assistant_messages AS m
     WHERE m.conversation_id = p_conversation_id
       AND m.restaurant_id = v_restaurant_id
       AND m.user_id = v_user_id
     ORDER BY m.created_at ASC, m.id ASC
     LIMIT 80;
END;
$function$;
REVOKE ALL ON FUNCTION private.studiomesa_assistant_list_messages_internal(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.studiomesa_assistant_list_messages_internal(uuid) TO authenticated;

CREATE OR REPLACE FUNCTION private.studiomesa_assistant_append_message_internal(
    p_conversation_id uuid,
    p_role text,
    p_content text,
    p_citations jsonb DEFAULT '[]'::jsonb
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_user_id uuid := (SELECT auth.uid());
    v_restaurant_id uuid;
    v_message_id uuid;
    v_content text := trim(coalesce(p_content, ''));
    v_citations jsonb := coalesce(p_citations, '[]'::jsonb);
BEGIN
    IF v_user_id IS NULL OR p_conversation_id IS NULL THEN
        RAISE EXCEPTION 'É necessário identificar a conversa e iniciar sessão.';
    END IF;
    IF p_role NOT IN ('user', 'assistant') THEN
        RAISE EXCEPTION 'O tipo de mensagem não é válido.';
    END IF;
    IF char_length(v_content) NOT BETWEEN 1 AND 12000 THEN
        RAISE EXCEPTION 'A mensagem deve ter entre 1 e 12000 caracteres.';
    END IF;
    IF jsonb_typeof(v_citations) <> 'array' OR jsonb_array_length(v_citations) > 20 THEN
        RAISE EXCEPTION 'A lista de fontes não é válida.';
    END IF;
    v_restaurant_id := private.studiomesa_assistant_owner_restaurant_id();

    PERFORM 1
      FROM private.studiomesa_assistant_conversations AS c
     WHERE c.id = p_conversation_id
       AND c.restaurant_id = v_restaurant_id
       AND c.user_id = v_user_id
     FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Esta conversa não pertence a esta conta.';
    END IF;

    IF p_role = 'user' AND (
        SELECT count(*)
          FROM private.studiomesa_assistant_messages AS m
         WHERE m.user_id = v_user_id
           AND m.created_at > now() - interval '1 minute'
           AND m.role = 'user'
    ) >= 8 THEN
        RAISE EXCEPTION 'Atingiste o limite temporário de mensagens. Aguarda um minuto e tenta novamente.';
    END IF;

    INSERT INTO private.studiomesa_assistant_messages
        (conversation_id, restaurant_id, user_id, role, content, citations)
    VALUES
        (p_conversation_id, v_restaurant_id, v_user_id, p_role, v_content,
         CASE WHEN p_role = 'assistant' THEN v_citations ELSE '[]'::jsonb END)
    RETURNING id INTO v_message_id;

    UPDATE private.studiomesa_assistant_conversations AS c
       SET updated_at = now(),
           title = CASE
               WHEN p_role = 'user' AND c.title = 'Nova conversa'
               THEN left(v_content, 72)
               ELSE c.title
           END
     WHERE c.id = p_conversation_id
       AND c.restaurant_id = v_restaurant_id
       AND c.user_id = v_user_id;

    RETURN v_message_id;
END;
$function$;
REVOKE ALL ON FUNCTION private.studiomesa_assistant_append_message_internal(uuid, text, text, jsonb) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.studiomesa_assistant_append_message_internal(uuid, text, text, jsonb) TO authenticated;

CREATE OR REPLACE FUNCTION private.studiomesa_assistant_delete_conversation_internal(p_conversation_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_user_id uuid := (SELECT auth.uid());
    v_restaurant_id uuid;
BEGIN
    IF v_user_id IS NULL OR p_conversation_id IS NULL THEN
        RAISE EXCEPTION 'É necessário identificar a conversa e iniciar sessão.';
    END IF;
    v_restaurant_id := private.studiomesa_assistant_owner_restaurant_id();
    DELETE FROM private.studiomesa_assistant_conversations AS c
     WHERE c.id = p_conversation_id
       AND c.restaurant_id = v_restaurant_id
       AND c.user_id = v_user_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Esta conversa não pertence a esta conta.';
    END IF;
END;
$function$;
REVOKE ALL ON FUNCTION private.studiomesa_assistant_delete_conversation_internal(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.studiomesa_assistant_delete_conversation_internal(uuid) TO authenticated;

CREATE OR REPLACE FUNCTION private.studiomesa_assistant_register_invoice_internal(
    p_invoice_id uuid,
    p_file_name text,
    p_file_size_bytes integer
)
RETURNS TABLE (invoice_id uuid, storage_path text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_user_id uuid := (SELECT auth.uid());
    v_restaurant_id uuid;
    v_file_name text := trim(coalesce(p_file_name, ''));
    v_path text;
    v_existing private.studiomesa_financial_invoices%ROWTYPE;
BEGIN
    IF v_user_id IS NULL OR p_invoice_id IS NULL THEN
        RAISE EXCEPTION 'É necessário identificar o ficheiro e iniciar sessão.';
    END IF;
    IF char_length(v_file_name) NOT BETWEEN 5 AND 180
       OR v_file_name !~* '^[^/\\]+[.]pdf$'
       OR p_file_size_bytes IS NULL
       OR p_file_size_bytes NOT BETWEEN 1 AND 10485760 THEN
        RAISE EXCEPTION 'A fatura deve ser um PDF até 10 MB, com um nome de ficheiro válido.';
    END IF;
    v_restaurant_id := private.studiomesa_assistant_owner_restaurant_id();
    v_path := v_restaurant_id::text || '/' || v_user_id::text || '/' || p_invoice_id::text || '.pdf';

    SELECT i.* INTO v_existing
      FROM private.studiomesa_financial_invoices AS i
     WHERE i.id = p_invoice_id
     FOR UPDATE;
    IF FOUND THEN
        IF v_existing.restaurant_id <> v_restaurant_id
           OR v_existing.uploaded_by <> v_user_id
           OR v_existing.file_name <> v_file_name
           OR v_existing.file_size_bytes <> p_file_size_bytes THEN
            RAISE EXCEPTION 'Este identificador de fatura já foi usado com outros dados.';
        END IF;
        RETURN QUERY SELECT v_existing.id, v_existing.storage_path;
        RETURN;
    END IF;

    INSERT INTO private.studiomesa_financial_invoices
        (id, restaurant_id, uploaded_by, storage_path, file_name, mime_type, file_size_bytes)
    VALUES
        (p_invoice_id, v_restaurant_id, v_user_id, v_path, v_file_name, 'application/pdf', p_file_size_bytes);
    RETURN QUERY SELECT p_invoice_id, v_path;
END;
$function$;
REVOKE ALL ON FUNCTION private.studiomesa_assistant_register_invoice_internal(uuid, text, integer) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.studiomesa_assistant_register_invoice_internal(uuid, text, integer) TO authenticated;

CREATE OR REPLACE FUNCTION private.studiomesa_assistant_complete_invoice_upload_internal(p_invoice_id uuid)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_user_id uuid := (SELECT auth.uid());
    v_restaurant_id uuid;
    v_path text;
    v_storage_object_exists boolean;
BEGIN
    IF v_user_id IS NULL OR p_invoice_id IS NULL THEN
        RAISE EXCEPTION 'É necessário identificar a fatura e iniciar sessão.';
    END IF;
    v_restaurant_id := private.studiomesa_assistant_owner_restaurant_id();

    SELECT i.storage_path INTO v_path
      FROM private.studiomesa_financial_invoices AS i
     WHERE i.id = p_invoice_id
       AND i.restaurant_id = v_restaurant_id
       AND i.uploaded_by = v_user_id
     FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'A fatura não pertence a esta conta.';
    END IF;

    IF to_regclass('storage.objects') IS NOT NULL THEN
        EXECUTE
            'SELECT EXISTS (
                SELECT 1
                  FROM storage.objects AS o
                 WHERE o.bucket_id = $1
                   AND o.name = $2
            )'
        INTO v_storage_object_exists
        USING 'restaurant-financial-invoices', v_path;
        IF NOT v_storage_object_exists THEN
            RAISE EXCEPTION 'O ficheiro PDF ainda não chegou ao arquivo privado.';
        END IF;
    END IF;

    UPDATE private.studiomesa_financial_invoices AS i
       SET upload_state = 'uploaded',
           uploaded_at = coalesce(i.uploaded_at, now())
     WHERE i.id = p_invoice_id
       AND i.restaurant_id = v_restaurant_id
       AND i.uploaded_by = v_user_id;
    RETURN true;
END;
$function$;
REVOKE ALL ON FUNCTION private.studiomesa_assistant_complete_invoice_upload_internal(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.studiomesa_assistant_complete_invoice_upload_internal(uuid) TO authenticated;

CREATE OR REPLACE FUNCTION private.studiomesa_assistant_list_invoices_internal()
RETURNS TABLE (invoice_id uuid, file_name text, file_size_bytes integer, uploaded_at timestamptz)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_user_id uuid := (SELECT auth.uid());
    v_restaurant_id uuid;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;
    v_restaurant_id := private.studiomesa_assistant_owner_restaurant_id();
    RETURN QUERY
    SELECT i.id, i.file_name, i.file_size_bytes, i.uploaded_at
      FROM private.studiomesa_financial_invoices AS i
     WHERE i.restaurant_id = v_restaurant_id
       AND i.upload_state = 'uploaded'
     ORDER BY i.uploaded_at DESC, i.id DESC
     LIMIT 50;
END;
$function$;
REVOKE ALL ON FUNCTION private.studiomesa_assistant_list_invoices_internal() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.studiomesa_assistant_list_invoices_internal() TO authenticated;

CREATE OR REPLACE FUNCTION private.studiomesa_assistant_get_invoice_files_internal(p_invoice_ids uuid[])
RETURNS TABLE (invoice_id uuid, storage_path text, file_name text, mime_type text, file_size_bytes integer)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_user_id uuid := (SELECT auth.uid());
    v_restaurant_id uuid;
    v_found integer;
BEGIN
    IF v_user_id IS NULL OR p_invoice_ids IS NULL
       OR cardinality(p_invoice_ids) NOT BETWEEN 1 AND 3
       OR array_position(p_invoice_ids, NULL) IS NOT NULL THEN
        RAISE EXCEPTION 'Seleciona entre uma e três faturas válidas.';
    END IF;
    IF cardinality(p_invoice_ids) <> (
        SELECT count(DISTINCT id) FROM unnest(p_invoice_ids) AS selected(id)
    ) THEN
        RAISE EXCEPTION 'A seleção contém faturas repetidas.';
    END IF;
    v_restaurant_id := private.studiomesa_assistant_owner_restaurant_id();

    SELECT count(*) INTO v_found
      FROM private.studiomesa_financial_invoices AS i
     WHERE i.id = ANY(p_invoice_ids)
       AND i.restaurant_id = v_restaurant_id
       AND i.upload_state = 'uploaded';
    IF v_found <> cardinality(p_invoice_ids) THEN
        RAISE EXCEPTION 'Uma ou mais faturas não pertencem a este restaurante ou ainda não foram carregadas.';
    END IF;

    RETURN QUERY
    SELECT i.id, i.storage_path, i.file_name, i.mime_type, i.file_size_bytes
      FROM private.studiomesa_financial_invoices AS i
     WHERE i.id = ANY(p_invoice_ids)
       AND i.restaurant_id = v_restaurant_id
       AND i.upload_state = 'uploaded'
     ORDER BY i.uploaded_at DESC
     LIMIT 3;
END;
$function$;
REVOKE ALL ON FUNCTION private.studiomesa_assistant_get_invoice_files_internal(uuid[]) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.studiomesa_assistant_get_invoice_files_internal(uuid[]) TO authenticated;

CREATE OR REPLACE FUNCTION private.studiomesa_assistant_business_context_internal()
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_user_id uuid := (SELECT auth.uid());
    v_restaurant_id uuid;
    v_context jsonb;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;
    v_restaurant_id := private.studiomesa_assistant_owner_restaurant_id();

    SELECT jsonb_build_object(
        'restaurant_name', r.name,
        'currency', 'EUR',
        'scope_note', 'Os valores são registos operacionais disponíveis; não equivalem a contabilidade certificada.',
        'menu_items', coalesce((
            SELECT jsonb_agg(jsonb_build_object(
                'name', m.name, 'price', m.price, 'unit_cost', m.cost
            ) ORDER BY m.name)
              FROM (
                  SELECT name, price, cost
                    FROM public.menu_items
                   WHERE restaurant_id = v_restaurant_id AND is_active = true
                   ORDER BY name
                   LIMIT 100
              ) AS m
        ), '[]'::jsonb),
        'orders', (
            SELECT jsonb_build_object(
                'active_count', count(*) FILTER (WHERE o.status NOT IN ('Concluído','Cancelado')),
                'completed_count', count(*) FILTER (WHERE o.status = 'Concluído'),
                'recorded_completed_amount_eur', coalesce(sum(o.amount) FILTER (WHERE o.status = 'Concluído'), 0)
            )
              FROM public.restaurant_orders AS o
             WHERE o.restaurant_id = v_restaurant_id
        ),
        'inventory', coalesce((
            SELECT jsonb_agg(jsonb_build_object(
                'name', s.name, 'unit', s.unit, 'quantity', s.quantity,
                'minimum', s.low_stock_threshold, 'unit_cost', s.unit_cost_per_unit
            ) ORDER BY s.name)
              FROM (
                  SELECT name, unit, quantity, low_stock_threshold, unit_cost_per_unit
                    FROM public.restaurant_inventory_items
                   WHERE restaurant_id = v_restaurant_id
                   ORDER BY name
                   LIMIT 100
              ) AS s
        ), '[]'::jsonb),
        'reservations', (
            SELECT jsonb_build_object(
                'pending', count(*) FILTER (WHERE rr.status = 'Pendente'),
                'confirmed', count(*) FILTER (WHERE rr.status = 'Confirmada')
            )
              FROM public.restaurant_reservations AS rr
             WHERE rr.restaurant_id = v_restaurant_id
        ),
        'historical_limit', 'Os preços e custos apresentados são os atuais; não existem snapshots históricos suficientes para calcular lucro passado por artigo.'
    )
      INTO v_context
      FROM public.restaurants AS r
     WHERE r.id = v_restaurant_id;

    RETURN coalesce(v_context, '{}'::jsonb);
END;
$function$;
REVOKE ALL ON FUNCTION private.studiomesa_assistant_business_context_internal() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.studiomesa_assistant_business_context_internal() TO authenticated;

-- Public Data API wrappers are SECURITY INVOKER and expose no table privileges.
CREATE OR REPLACE FUNCTION public.studiomesa_assistant_list_conversations(p_query text DEFAULT NULL)
RETURNS TABLE (conversation_id uuid, title text, created_at timestamptz, updated_at timestamptz, last_message_preview text)
LANGUAGE sql STABLE SECURITY INVOKER SET search_path TO ''
AS $function$
    SELECT * FROM private.studiomesa_assistant_list_conversations_internal(p_query);
$function$;

CREATE OR REPLACE FUNCTION public.studiomesa_assistant_create_conversation(p_title text DEFAULT NULL)
RETURNS TABLE (conversation_id uuid, title text, created_at timestamptz)
LANGUAGE sql SECURITY INVOKER SET search_path TO ''
AS $function$
    SELECT * FROM private.studiomesa_assistant_create_conversation_internal(p_title);
$function$;

CREATE OR REPLACE FUNCTION public.studiomesa_assistant_list_messages(p_conversation_id uuid)
RETURNS TABLE (message_id uuid, role text, content text, citations jsonb, created_at timestamptz)
LANGUAGE sql STABLE SECURITY INVOKER SET search_path TO ''
AS $function$
    SELECT * FROM private.studiomesa_assistant_list_messages_internal(p_conversation_id);
$function$;

CREATE OR REPLACE FUNCTION public.studiomesa_assistant_append_message(
    p_conversation_id uuid,
    p_role text,
    p_content text,
    p_citations jsonb DEFAULT '[]'::jsonb
)
RETURNS uuid
LANGUAGE sql SECURITY INVOKER SET search_path TO ''
AS $function$
    SELECT private.studiomesa_assistant_append_message_internal(
        p_conversation_id, p_role, p_content, p_citations
    );
$function$;

CREATE OR REPLACE FUNCTION public.studiomesa_assistant_delete_conversation(p_conversation_id uuid)
RETURNS void
LANGUAGE sql SECURITY INVOKER SET search_path TO ''
AS $function$
    SELECT private.studiomesa_assistant_delete_conversation_internal(p_conversation_id);
$function$;

CREATE OR REPLACE FUNCTION public.studiomesa_assistant_register_invoice(
    p_invoice_id uuid,
    p_file_name text,
    p_file_size_bytes integer
)
RETURNS TABLE (invoice_id uuid, storage_path text)
LANGUAGE sql SECURITY INVOKER SET search_path TO ''
AS $function$
    SELECT * FROM private.studiomesa_assistant_register_invoice_internal(
        p_invoice_id, p_file_name, p_file_size_bytes
    );
$function$;

CREATE OR REPLACE FUNCTION public.studiomesa_assistant_complete_invoice_upload(p_invoice_id uuid)
RETURNS boolean
LANGUAGE sql SECURITY INVOKER SET search_path TO ''
AS $function$
    SELECT private.studiomesa_assistant_complete_invoice_upload_internal(p_invoice_id);
$function$;

CREATE OR REPLACE FUNCTION public.studiomesa_assistant_list_invoices()
RETURNS TABLE (invoice_id uuid, file_name text, file_size_bytes integer, uploaded_at timestamptz)
LANGUAGE sql STABLE SECURITY INVOKER SET search_path TO ''
AS $function$
    SELECT * FROM private.studiomesa_assistant_list_invoices_internal();
$function$;

CREATE OR REPLACE FUNCTION public.studiomesa_assistant_get_invoice_files(p_invoice_ids uuid[])
RETURNS TABLE (invoice_id uuid, storage_path text, file_name text, mime_type text, file_size_bytes integer)
LANGUAGE sql STABLE SECURITY INVOKER SET search_path TO ''
AS $function$
    SELECT * FROM private.studiomesa_assistant_get_invoice_files_internal(p_invoice_ids);
$function$;

CREATE OR REPLACE FUNCTION public.studiomesa_assistant_get_business_context()
RETURNS jsonb
LANGUAGE sql STABLE SECURITY INVOKER SET search_path TO ''
AS $function$
    SELECT private.studiomesa_assistant_business_context_internal();
$function$;

REVOKE ALL ON FUNCTION public.studiomesa_assistant_list_conversations(text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.studiomesa_assistant_create_conversation(text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.studiomesa_assistant_list_messages(uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.studiomesa_assistant_append_message(uuid, text, text, jsonb) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.studiomesa_assistant_delete_conversation(uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.studiomesa_assistant_register_invoice(uuid, text, integer) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.studiomesa_assistant_complete_invoice_upload(uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.studiomesa_assistant_list_invoices() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.studiomesa_assistant_get_invoice_files(uuid[]) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.studiomesa_assistant_get_business_context() FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION public.studiomesa_assistant_list_conversations(text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.studiomesa_assistant_create_conversation(text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.studiomesa_assistant_list_messages(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.studiomesa_assistant_append_message(uuid, text, text, jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION public.studiomesa_assistant_delete_conversation(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.studiomesa_assistant_register_invoice(uuid, text, integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.studiomesa_assistant_complete_invoice_upload(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.studiomesa_assistant_list_invoices() TO authenticated;
GRANT EXECUTE ON FUNCTION public.studiomesa_assistant_get_invoice_files(uuid[]) TO authenticated;
GRANT EXECUTE ON FUNCTION public.studiomesa_assistant_get_business_context() TO authenticated;

-- Create a private, PDF-only Storage bucket where supported by the hosted Storage schema.
-- The migration replay CI uses plain PostgreSQL, where the storage schema is absent.
DO $storage$
BEGIN
    IF to_regclass('storage.buckets') IS NOT NULL THEN
        EXECUTE $sql$
            INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
            VALUES ('restaurant-financial-invoices', 'restaurant-financial-invoices', false, 10485760, ARRAY['application/pdf']::text[])
            ON CONFLICT (id) DO UPDATE
                SET public = false,
                    file_size_limit = 10485760,
                    allowed_mime_types = ARRAY['application/pdf']::text[]
        $sql$;
    END IF;

    IF to_regclass('storage.objects') IS NOT NULL THEN
        EXECUTE $policy$
            DROP POLICY IF EXISTS "StudioMesa owners read financial invoices" ON storage.objects;
            CREATE POLICY "StudioMesa owners read financial invoices"
                ON storage.objects FOR SELECT TO authenticated
                USING (
                    bucket_id = 'restaurant-financial-invoices'
                    AND EXISTS (
                        SELECT 1 FROM public.restaurant_staff AS rs
                         WHERE rs.restaurant_id::text = split_part(name, '/', 1)
                           AND rs.user_id = (SELECT auth.uid())
                           AND rs.role = 'owner'
                    )
                )
        $policy$;
        EXECUTE $policy$
            DROP POLICY IF EXISTS "StudioMesa owners upload financial invoices" ON storage.objects;
            CREATE POLICY "StudioMesa owners upload financial invoices"
                ON storage.objects FOR INSERT TO authenticated
                WITH CHECK (
                    bucket_id = 'restaurant-financial-invoices'
                    AND split_part(name, '/', 2) = (SELECT auth.uid())::text
                    AND split_part(name, '/', 3) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}[.]pdf$'
                    AND EXISTS (
                        SELECT 1 FROM public.restaurant_staff AS rs
                         WHERE rs.restaurant_id::text = split_part(name, '/', 1)
                           AND rs.user_id = (SELECT auth.uid())
                           AND rs.role = 'owner'
                    )
                )
        $policy$;
        EXECUTE $policy$
            DROP POLICY IF EXISTS "StudioMesa owners delete financial invoices" ON storage.objects;
            CREATE POLICY "StudioMesa owners delete financial invoices"
                ON storage.objects FOR DELETE TO authenticated
                USING (
                    bucket_id = 'restaurant-financial-invoices'
                    AND EXISTS (
                        SELECT 1 FROM public.restaurant_staff AS rs
                         WHERE rs.restaurant_id::text = split_part(name, '/', 1)
                           AND rs.user_id = (SELECT auth.uid())
                           AND rs.role = 'owner'
                    )
                )
        $policy$;
    END IF;
END;
$storage$;

COMMIT;
