BEGIN;

-- The Android archive remains AES-GCM encrypted on each device. This private
-- table stores the synchronized invoice payload under the restaurant owner scope;
-- Supabase encrypts the database volume at rest and this schema is not exposed by
-- the PostgREST Data API. Only owner-checked RPCs can read or write these rows.
CREATE TABLE private.restaurant_saft_sales_invoices (
    restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
    invoice_id text NOT NULL CHECK (invoice_id ~ '^[a-f0-9]{64}$'),
    issue_date date NOT NULL,
    updated_at_ms bigint NOT NULL CHECK (updated_at_ms > 0),
    payload jsonb NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    updated_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
    PRIMARY KEY (restaurant_id, invoice_id)
);

CREATE INDEX restaurant_saft_sales_invoices_restaurant_date_idx
    ON private.restaurant_saft_sales_invoices (restaurant_id, issue_date, invoice_id);
CREATE INDEX restaurant_saft_sales_invoices_updated_idx
    ON private.restaurant_saft_sales_invoices (restaurant_id, updated_at_ms DESC);

ALTER TABLE private.restaurant_saft_sales_invoices ENABLE ROW LEVEL SECURITY;

CREATE POLICY restaurant_saft_sales_invoices_owner_all
ON private.restaurant_saft_sales_invoices
FOR ALL TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.restaurant_staff rs
        WHERE rs.restaurant_id = restaurant_saft_sales_invoices.restaurant_id
          AND rs.user_id = (SELECT auth.uid())
          AND rs.role = 'owner'
    )
)
WITH CHECK (
    updated_by = (SELECT auth.uid())
    AND EXISTS (
        SELECT 1 FROM public.restaurant_staff rs
        WHERE rs.restaurant_id = restaurant_saft_sales_invoices.restaurant_id
          AND rs.user_id = (SELECT auth.uid())
          AND rs.role = 'owner'
    )
);

REVOKE ALL PRIVILEGES ON TABLE private.restaurant_saft_sales_invoices FROM PUBLIC, anon, authenticated;

-- Upload one bounded batch of changed rows. A later timestamp wins; an older
-- device cannot overwrite a newer version that has already synchronized.
CREATE OR REPLACE FUNCTION public.sync_restaurant_saft_sales_invoice_batch(
    p_restaurant_id uuid,
    p_rows jsonb
)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_uid uuid := (SELECT auth.uid());
    v_row jsonb;
    v_payload jsonb;
    v_invoice_id text;
    v_issue_date_text text;
    v_issue_date date;
    v_updated_at_ms bigint;
    v_line_count integer;
    v_processed integer := 0;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;
    IF p_restaurant_id IS NULL OR NOT EXISTS (
        SELECT 1
        FROM public.restaurant_staff rs
        WHERE rs.restaurant_id = p_restaurant_id
          AND rs.user_id = v_uid
          AND rs.role = 'owner'
    ) THEN
        RAISE EXCEPTION 'A sincronização SAF-T está reservada ao proprietário do restaurante.';
    END IF;
    IF p_rows IS NULL OR jsonb_typeof(p_rows) <> 'array' THEN
        RAISE EXCEPTION 'O lote SAF-T não tem um formato válido.';
    END IF;
    IF jsonb_array_length(p_rows) > 500 OR octet_length(p_rows::text) > 4194304 THEN
        RAISE EXCEPTION 'O lote SAF-T excede o limite seguro de sincronização.';
    END IF;

    FOR v_row IN SELECT value FROM jsonb_array_elements(p_rows)
    LOOP
        v_invoice_id := v_row->>'invoice_id';
        v_issue_date_text := v_row->>'issue_date';
        v_payload := v_row->'payload';

        IF v_invoice_id IS NULL OR v_invoice_id !~ '^[a-f0-9]{64}$'
           OR v_issue_date_text IS NULL OR v_issue_date_text !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
           OR jsonb_typeof(v_payload) IS DISTINCT FROM 'object' THEN
            RAISE EXCEPTION 'Uma fatura SAF-T do lote contém campos inválidos.';
        END IF;
        v_issue_date := v_issue_date_text::date;
        IF to_char(v_issue_date, 'YYYY-MM-DD') <> v_issue_date_text
           OR v_payload->>'id' IS DISTINCT FROM v_invoice_id
           OR v_payload->>'issue_date' IS DISTINCT FROM v_issue_date_text
           OR char_length(coalesce(v_payload->>'invoice_number', '')) NOT BETWEEN 1 AND 100
           OR v_payload->>'invoice_type' IS NULL
           OR v_payload->>'invoice_type' NOT IN ('FT', 'FS', 'FR', 'ND', 'NC', 'VD', 'TV', 'TD') THEN
            RAISE EXCEPTION 'A identidade ou os campos principais de uma fatura SAF-T não são válidos.';
        END IF;
        IF coalesce(v_payload->>'net_total', '') !~ '^-?[0-9]{1,12}(\\.[0-9]{1,2})?$'
           OR coalesce(v_payload->>'gross_total', '') !~ '^-?[0-9]{1,12}(\\.[0-9]{1,2})?$' THEN
            RAISE EXCEPTION 'Os totais de uma fatura SAF-T não são numéricos.';
        END IF;
        IF jsonb_typeof(v_payload->'lines') IS DISTINCT FROM 'array' THEN
            RAISE EXCEPTION 'As linhas de uma fatura SAF-T não têm um formato válido.';
        END IF;
        v_line_count := jsonb_array_length(v_payload->'lines');
        IF v_line_count > 2000 OR octet_length(v_payload::text) > 262144 THEN
            RAISE EXCEPTION 'Uma fatura SAF-T excede os limites de linhas ou tamanho.';
        END IF;
        v_updated_at_ms := (v_row->>'updated_at_ms')::bigint;
        IF v_updated_at_ms IS NULL OR v_updated_at_ms <= 0 THEN
            RAISE EXCEPTION 'A data de atualização da fatura SAF-T não é válida.';
        END IF;

        INSERT INTO private.restaurant_saft_sales_invoices (
            restaurant_id, invoice_id, issue_date, updated_at_ms, payload, updated_by
        )
        VALUES (
            p_restaurant_id, v_invoice_id, v_issue_date, v_updated_at_ms, v_payload, v_uid
        )
        ON CONFLICT (restaurant_id, invoice_id) DO UPDATE
        SET issue_date = EXCLUDED.issue_date,
            updated_at_ms = EXCLUDED.updated_at_ms,
            payload = EXCLUDED.payload,
            updated_by = EXCLUDED.updated_by
        WHERE EXCLUDED.updated_at_ms >
              private.restaurant_saft_sales_invoices.updated_at_ms;

        v_processed := v_processed + 1;
    END LOOP;

    RETURN v_processed;
END;
$function$;

REVOKE ALL ON FUNCTION public.sync_restaurant_saft_sales_invoice_batch(uuid, jsonb) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.sync_restaurant_saft_sales_invoice_batch(uuid, jsonb) TO authenticated;

CREATE OR REPLACE FUNCTION public.list_restaurant_saft_sales_invoice_batch(
    p_restaurant_id uuid,
    p_offset integer DEFAULT 0,
    p_limit integer DEFAULT 500
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_uid uuid := (SELECT auth.uid());
    v_result jsonb;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;
    IF p_restaurant_id IS NULL OR NOT EXISTS (
        SELECT 1
        FROM public.restaurant_staff rs
        WHERE rs.restaurant_id = p_restaurant_id
          AND rs.user_id = v_uid
          AND rs.role = 'owner'
    ) THEN
        RAISE EXCEPTION 'O arquivo SAF-T partilhado está reservado ao proprietário do restaurante.';
    END IF;
    IF p_offset IS NULL OR p_offset < 0 OR p_offset > 1000000
       OR p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 500 THEN
        RAISE EXCEPTION 'A página do arquivo SAF-T não é válida.';
    END IF;

    SELECT jsonb_build_object(
        'rows',
        coalesce((
            SELECT jsonb_agg(
                jsonb_build_object(
                    'invoice_id', page.invoice_id,
                    'issue_date', page.issue_date,
                    'updated_at_ms', page.updated_at_ms,
                    'payload', page.payload
                )
                ORDER BY page.issue_date, page.invoice_id
            )
            FROM (
                SELECT invoice_id, issue_date, updated_at_ms, payload
                FROM private.restaurant_saft_sales_invoices
                WHERE restaurant_id = p_restaurant_id
                ORDER BY issue_date, invoice_id
                OFFSET p_offset
                LIMIT p_limit
            ) AS page
        ), '[]'::jsonb),
        'total_count',
        (
            SELECT count(*)::integer
            FROM private.restaurant_saft_sales_invoices
            WHERE restaurant_id = p_restaurant_id
        )
    )
    INTO v_result;

    RETURN v_result;
END;
$function$;

REVOKE ALL ON FUNCTION public.list_restaurant_saft_sales_invoice_batch(uuid, integer, integer) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.list_restaurant_saft_sales_invoice_batch(uuid, integer, integer) TO authenticated;

-- Product lines are reviewed by the owner before approval. Stock receipt is a
-- separate action, so OCR never changes quantities by itself.
ALTER TABLE public.restaurant_supplier_invoices
    ADD COLUMN IF NOT EXISTS line_items jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS stock_received_at timestamptz,
    ADD COLUMN IF NOT EXISTS stock_received_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS stock_receipt_request_id uuid;

ALTER TABLE public.restaurant_supplier_invoices
    DROP CONSTRAINT IF EXISTS restaurant_supplier_invoices_line_items_check,
    ADD CONSTRAINT restaurant_supplier_invoices_line_items_check
        CHECK (
            jsonb_typeof(line_items) = 'array'
            AND jsonb_array_length(line_items) <= 500
        );

ALTER TABLE public.restaurant_supplier_invoices
    DROP CONSTRAINT IF EXISTS restaurant_supplier_invoices_stock_receipt_fields_check,
    ADD CONSTRAINT restaurant_supplier_invoices_stock_receipt_fields_check
        CHECK (
            (stock_received_at IS NULL AND stock_received_by IS NULL AND stock_receipt_request_id IS NULL)
            OR
            (stock_received_at IS NOT NULL AND stock_receipt_request_id IS NOT NULL)
        );

CREATE TABLE private.restaurant_supplier_invoice_stock_receipts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
    invoice_id uuid NOT NULL REFERENCES public.restaurant_supplier_invoices(id) ON DELETE RESTRICT,
    request_id uuid NOT NULL,
    mapping jsonb NOT NULL CHECK (jsonb_typeof(mapping) = 'array'),
    received_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
    received_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (restaurant_id, invoice_id),
    UNIQUE (restaurant_id, request_id)
);

CREATE INDEX restaurant_supplier_invoice_stock_receipts_received_idx
    ON private.restaurant_supplier_invoice_stock_receipts (restaurant_id, received_at DESC);

ALTER TABLE private.restaurant_supplier_invoice_stock_receipts ENABLE ROW LEVEL SECURITY;

CREATE POLICY restaurant_supplier_invoice_stock_receipts_owner_select
ON private.restaurant_supplier_invoice_stock_receipts
FOR SELECT TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.restaurant_staff rs
        WHERE rs.restaurant_id = restaurant_supplier_invoice_stock_receipts.restaurant_id
          AND rs.user_id = (SELECT auth.uid())
          AND rs.role = 'owner'
    )
);

REVOKE ALL PRIVILEGES ON TABLE private.restaurant_supplier_invoice_stock_receipts FROM PUBLIC, anon, authenticated;

-- Once a receipt has been committed, its reviewed invoice lines cannot be changed.
CREATE OR REPLACE FUNCTION private.guard_supplier_invoice_received_lines()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $function$
BEGIN
    IF OLD.stock_received_at IS NOT NULL
       AND (
           NEW.line_items IS DISTINCT FROM OLD.line_items
           OR NEW.total_amount IS DISTINCT FROM OLD.total_amount
           OR NEW.review_status IS DISTINCT FROM OLD.review_status
       ) THEN
        RAISE EXCEPTION 'Uma fatura já recebida no stock não pode alterar as linhas, o total ou o estado.';
    END IF;
    RETURN NEW;
END;
$function$;

REVOKE ALL ON FUNCTION private.guard_supplier_invoice_received_lines() FROM PUBLIC, anon, authenticated;

CREATE TRIGGER restaurant_supplier_invoice_received_lines_immutable
BEFORE UPDATE ON public.restaurant_supplier_invoices
FOR EACH ROW
EXECUTE FUNCTION private.guard_supplier_invoice_received_lines();

-- Only the authenticated restaurant owner may receive goods. The selected line
-- IDs and quantities come from the saved, reviewed OCR record, while the owner
-- only maps each line to an existing stock item with the same unit.
CREATE OR REPLACE FUNCTION public.receive_supplier_invoice_stock(
    p_invoice_id uuid,
    p_request_id uuid,
    p_lines jsonb
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_uid uuid := (SELECT auth.uid());
    v_invoice public.restaurant_supplier_invoices%rowtype;
    v_existing private.restaurant_supplier_invoice_stock_receipts%rowtype;
    v_mapping jsonb := '[]'::jsonb;
    v_map jsonb;
    v_line jsonb;
    v_line_id text;
    v_inventory_id uuid;
    v_accepted_quantity_text text;
    v_accepted_quantity numeric;
    v_seen_line_ids text[] := ARRAY[]::text[];
    v_selected_count integer;
    v_valid_count integer := 0;
    v_item record;
    v_old_quantity numeric;
    v_new_quantity numeric;
    v_old_cost numeric;
    v_new_cost numeric;
    v_stock_unit text;
    v_receipt_id uuid;
    v_received_items integer := 0;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;
    IF p_invoice_id IS NULL OR p_request_id IS NULL THEN
        RAISE EXCEPTION 'A fatura e o identificador de receção são obrigatórios.';
    END IF;
    IF p_lines IS NULL OR jsonb_typeof(p_lines) <> 'array' THEN
        RAISE EXCEPTION 'Seleciona pelo menos uma linha da fatura para receber.';
    END IF;
    v_selected_count := jsonb_array_length(p_lines);
    IF v_selected_count NOT BETWEEN 1 AND 500 THEN
        RAISE EXCEPTION 'O número de linhas selecionadas não é válido.';
    END IF;

    SELECT invoice.*
      INTO v_invoice
      FROM public.restaurant_supplier_invoices AS invoice
     WHERE invoice.id = p_invoice_id
       AND EXISTS (
           SELECT 1 FROM public.restaurant_staff rs
           WHERE rs.restaurant_id = invoice.restaurant_id
             AND rs.user_id = v_uid
             AND rs.role = 'owner'
       )
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Fatura não encontrada ou sem autorização do proprietário.';
    END IF;
    IF v_invoice.review_status <> 'approved' THEN
        RAISE EXCEPTION 'Só podes receber mercadoria depois de confirmares a fatura.';
    END IF;
    IF jsonb_array_length(v_invoice.line_items) = 0 THEN
        RAISE EXCEPTION 'Esta fatura não tem linhas de produtos reconhecidas. Revê ou introduz as linhas antes da receção.';
    END IF;

    -- Validate each mapping against the exact reviewed line. The owner may correct
    -- an OCR quantity in the receipt dialog, but the server validates its precision
    -- and continues to trust the saved invoice for the product, unit and net amount.
    FOR v_map IN SELECT value FROM jsonb_array_elements(p_lines)
    LOOP
        v_line_id := v_map->>'line_id';
        IF v_line_id IS NULL OR v_line_id !~ '^[a-f0-9]{64}$'
           OR (v_map->>'inventory_item_id') IS NULL THEN
            RAISE EXCEPTION 'Uma linha não tem uma identificação válida.';
        END IF;
        IF v_line_id = ANY(v_seen_line_ids) THEN
            RAISE EXCEPTION 'A mesma linha da fatura foi selecionada mais do que uma vez.';
        END IF;
        v_seen_line_ids := array_append(v_seen_line_ids, v_line_id);
        v_accepted_quantity_text := v_map->>'accepted_quantity';
        IF v_accepted_quantity_text IS NULL
           OR v_accepted_quantity_text !~ '^[0-9]+([.][0-9]{1,3})?$' THEN
            RAISE EXCEPTION 'Indica uma quantidade positiva com, no máximo, três casas decimais.';
        END IF;
        v_accepted_quantity := v_accepted_quantity_text::numeric;
        IF v_accepted_quantity <= 0 OR v_accepted_quantity > 999999 THEN
            RAISE EXCEPTION 'A quantidade recebida está fora do intervalo permitido.';
        END IF;

        BEGIN
            v_inventory_id := (v_map->>'inventory_item_id')::uuid;
        EXCEPTION WHEN invalid_text_representation THEN
            RAISE EXCEPTION 'Um artigo de stock selecionado não é válido.';
        END;

        SELECT value INTO v_line
        FROM jsonb_array_elements(v_invoice.line_items)
        WHERE value->>'id' = v_line_id;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Uma linha selecionada não pertence à fatura guardada.';
        END IF;
        IF coalesce(v_line->>'description', '') = ''
           OR coalesce(v_line->>'unit', '') = ''
           OR coalesce(v_line->>'quantity', '') !~ '^[0-9]+(\.[0-9]{1,4})?$'
           OR (v_line->>'quantity')::numeric <= 0
           OR (v_line->>'quantity')::numeric > 999999
           OR coalesce(v_line->>'net_amount', '') !~ '^[0-9]+(\.[0-9]{1,2})?$'
           OR (v_line->>'net_amount')::numeric < 0 THEN
            RAISE EXCEPTION 'Uma linha da fatura contém quantidade ou valor inválido.';
        END IF;

        v_mapping := v_mapping || jsonb_build_array(
            jsonb_build_object(
                'line_id', v_line_id,
                'inventory_item_id', v_inventory_id,
                'accepted_quantity', v_accepted_quantity::text
            )
        );
        v_valid_count := v_valid_count + 1;
    END LOOP;

    SELECT coalesce(jsonb_agg(value ORDER BY value->>'line_id'), '[]'::jsonb)
      INTO v_mapping
      FROM jsonb_array_elements(v_mapping);

    -- If the response was lost after commit, an identical retry confirms the
    -- original receipt without changing stock again. A different mapping is rejected.
    SELECT receipt.*
      INTO v_existing
      FROM private.restaurant_supplier_invoice_stock_receipts AS receipt
     WHERE receipt.restaurant_id = v_invoice.restaurant_id
       AND receipt.invoice_id = p_invoice_id
     FOR UPDATE;

    IF FOUND THEN
        IF v_existing.mapping IS DISTINCT FROM v_mapping THEN
            RAISE EXCEPTION 'Esta fatura já foi recebida no stock com outra seleção. Atualiza o arquivo antes de tentar novamente.';
        END IF;
        RETURN jsonb_build_object(
            'already_applied', true,
            'receipt_id', v_existing.id,
            'received_at', v_existing.received_at,
            'stock_items', (
                SELECT count(DISTINCT value->>'inventory_item_id')
                FROM jsonb_array_elements(v_existing.mapping)
            )
        );
    END IF;

    IF v_invoice.stock_received_at IS NOT NULL THEN
        RAISE EXCEPTION 'A fatura já está marcada como recebida no stock. Atualiza o arquivo partilhado.';
    END IF;

    INSERT INTO private.restaurant_supplier_invoice_stock_receipts (
        restaurant_id, invoice_id, request_id, mapping, received_by
    )
    VALUES (
        v_invoice.restaurant_id, p_invoice_id, p_request_id, v_mapping, v_uid
    )
    RETURNING id INTO v_receipt_id;

    -- Aggregate multiple invoice lines mapped to the same stock item.
    FOR v_item IN
        WITH selected_lines AS (
            SELECT
                (mapping.value->>'inventory_item_id')::uuid AS item_id,
                lower(trim(line.value->>'unit')) AS line_unit,
                (mapping.value->>'accepted_quantity')::numeric AS quantity,
                (line.value->>'net_amount')::numeric AS net_amount
            FROM jsonb_array_elements(v_mapping) AS mapping(value)
            JOIN jsonb_array_elements(v_invoice.line_items) AS line(value)
              ON line.value->>'id' = mapping.value->>'line_id'
        )
        SELECT
            item_id,
            min(line_unit) AS line_unit,
            max(line_unit) AS line_unit_max,
            sum(quantity) AS quantity,
            sum(net_amount) AS net_amount
        FROM selected_lines
        GROUP BY item_id
        ORDER BY item_id
    LOOP
        SELECT
            item.unit,
            item.quantity,
            item.unit_cost_per_unit
        INTO v_stock_unit, v_old_quantity, v_old_cost
        FROM public.restaurant_inventory_items AS item
        WHERE item.id = v_item.item_id
          AND item.restaurant_id = v_invoice.restaurant_id
        FOR UPDATE;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Um dos artigos de stock já não pertence a este restaurante.';
        END IF;
        IF v_item.line_unit <> v_item.line_unit_max THEN
            RAISE EXCEPTION 'As linhas mapeadas para o mesmo artigo usam unidades diferentes. Separa os artigos de stock.';
        END IF;

        v_stock_unit := CASE lower(trim(v_stock_unit))
            WHEN 'unidades' THEN 'un'
            WHEN 'unidade' THEN 'un'
            WHEN 'und' THEN 'un'
            WHEN 'gr' THEN 'g'
            WHEN 'litro' THEN 'l'
            WHEN 'litros' THEN 'l'
            WHEN 'lt' THEN 'l'
            ELSE lower(trim(v_stock_unit))
        END;
        IF v_stock_unit <> v_item.line_unit THEN
            RAISE EXCEPTION 'A unidade do stock (%) não coincide com a unidade da fatura (%). Converte a unidade manualmente antes de receber.', v_stock_unit, v_item.line_unit;
        END IF;
        IF v_item.quantity <> round(v_item.quantity, 3) THEN
            RAISE EXCEPTION 'O stock admite três casas decimais. Corrige a quantidade da linha antes de receber.';
        END IF;

        v_new_quantity := v_old_quantity + v_item.quantity;
        IF v_new_quantity < 0 OR v_new_quantity > 999999999 THEN
            RAISE EXCEPTION 'A quantidade resultante de stock está fora do intervalo permitido.';
        END IF;

        IF v_item.net_amount > 0 AND v_new_quantity > 0 THEN
            IF v_old_cost IS NULL OR v_old_quantity <= 0 THEN
                v_new_cost := round(v_item.net_amount / v_item.quantity, 4);
            ELSE
                v_new_cost := round(
                    ((v_old_quantity * v_old_cost) + v_item.net_amount) / v_new_quantity,
                    4
                );
            END IF;
        ELSE
            v_new_cost := v_old_cost;
        END IF;

        UPDATE public.restaurant_inventory_items
           SET quantity = v_new_quantity,
               unit_cost_per_unit = v_new_cost,
               updated_at = now()
         WHERE id = v_item.item_id;

        INSERT INTO public.restaurant_inventory_movements (
            restaurant_id, item_id, delta, quantity_after, note,
            created_by, client_request_id, unit_cost_at_movement
        )
        VALUES (
            v_invoice.restaurant_id,
            v_item.item_id,
            v_item.quantity,
            v_new_quantity,
            left('Receção da fatura ' || v_invoice.invoice_number || ' · ' || v_invoice.supplier_name, 500),
            v_uid,
            md5(p_invoice_id::text || ':' || v_item.item_id::text)::uuid,
            v_new_cost
        );

        v_received_items := v_received_items + 1;
    END LOOP;

    UPDATE public.restaurant_supplier_invoices
       SET stock_received_at = now(),
           stock_received_by = v_uid,
           stock_receipt_request_id = p_request_id,
           updated_at = now()
     WHERE id = p_invoice_id;

    RETURN jsonb_build_object(
        'already_applied', false,
        'receipt_id', v_receipt_id,
        'received_at', now(),
        'stock_items', v_received_items,
        'mapped_lines', v_valid_count
    );
END;
$function$;

REVOKE ALL ON FUNCTION public.receive_supplier_invoice_stock(uuid, uuid, jsonb) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.receive_supplier_invoice_stock(uuid, uuid, jsonb) TO authenticated;

COMMIT;
