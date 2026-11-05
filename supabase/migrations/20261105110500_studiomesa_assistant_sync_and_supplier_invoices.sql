BEGIN;

-- Owner-only shared assistant history, memory and supplier-invoice metadata.
-- Raw OCR text is deliberately not stored. The owner reviews extracted fields first.

CREATE TABLE public.restaurant_assistant_conversations (
    id uuid PRIMARY KEY,
    restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
    title text NOT NULL CHECK (char_length(btrim(title)) BETWEEN 1 AND 120),
    created_by uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, restaurant_id)
);

CREATE TABLE public.restaurant_assistant_messages (
    id uuid PRIMARY KEY,
    restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
    conversation_id uuid NOT NULL,
    role text NOT NULL CHECK (role IN ('user', 'assistant')),
    content text NOT NULL CHECK (char_length(btrim(content)) BETWEEN 1 AND 6000),
    created_by uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT restaurant_assistant_messages_conversation_fk
        FOREIGN KEY (conversation_id, restaurant_id)
        REFERENCES public.restaurant_assistant_conversations (id, restaurant_id)
        ON DELETE CASCADE
);

CREATE TABLE public.restaurant_assistant_memory (
    restaurant_id uuid PRIMARY KEY REFERENCES public.restaurants(id) ON DELETE CASCADE,
    content text NOT NULL DEFAULT '' CHECK (char_length(content) <= 6000),
    updated_by uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.restaurant_supplier_invoices (
    id uuid PRIMARY KEY,
    restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
    supplier_name text NOT NULL CHECK (char_length(btrim(supplier_name)) BETWEEN 1 AND 100),
    supplier_vat_number text NOT NULL DEFAULT '' CHECK (char_length(supplier_vat_number) <= 30),
    invoice_number text NOT NULL CHECK (char_length(btrim(invoice_number)) BETWEEN 1 AND 40),
    issue_date date NOT NULL,
    due_date date,
    currency text NOT NULL DEFAULT 'EUR' CHECK (currency ~ '^[A-Z]{3}$'),
    net_amount numeric(14,2) CHECK (net_amount IS NULL OR net_amount >= 0),
    vat_amount numeric(14,2) CHECK (vat_amount IS NULL OR vat_amount >= 0),
    total_amount numeric(14,2) NOT NULL CHECK (total_amount > 0),
    review_status text NOT NULL DEFAULT 'pending_review'
        CHECK (review_status IN ('pending_review', 'approved', 'rejected')),
    source_type text NOT NULL
        CHECK (source_type IN ('pdf_ocr', 'image_ocr', 'csv_import', 'manual')),
    mime_type text CHECK (mime_type IS NULL OR mime_type IN ('application/pdf', 'image/jpeg', 'image/png', 'image/webp', 'text/csv')),
    byte_size bigint CHECK (byte_size IS NULL OR byte_size BETWEEN 1 AND 26214400),
    document_sha256 text CHECK (document_sha256 IS NULL OR document_sha256 ~ '^[a-f0-9]{64}$'),
    document_path text CHECK (document_path IS NULL OR char_length(document_path) <= 500),
    warnings text[] NOT NULL DEFAULT ARRAY[]::text[],
    created_by uuid NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    reviewed_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
    reviewed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT restaurant_supplier_invoices_review_fields
        CHECK (
            (review_status = 'pending_review' AND reviewed_at IS NULL)
            OR (review_status IN ('approved', 'rejected') AND reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL)
        )
);

CREATE INDEX restaurant_assistant_conversations_restaurant_updated_idx
    ON public.restaurant_assistant_conversations (restaurant_id, updated_at DESC);
CREATE INDEX restaurant_assistant_messages_conversation_created_idx
    ON public.restaurant_assistant_messages (conversation_id, created_at);
CREATE INDEX restaurant_supplier_invoices_restaurant_date_idx
    ON public.restaurant_supplier_invoices (restaurant_id, issue_date DESC, created_at DESC);
CREATE INDEX restaurant_supplier_invoices_restaurant_status_idx
    ON public.restaurant_supplier_invoices (restaurant_id, review_status, due_date);

ALTER TABLE public.restaurant_assistant_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.restaurant_assistant_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.restaurant_assistant_memory ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.restaurant_supplier_invoices ENABLE ROW LEVEL SECURITY;

REVOKE ALL PRIVILEGES ON TABLE public.restaurant_assistant_conversations FROM PUBLIC, anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.restaurant_assistant_messages FROM PUBLIC, anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.restaurant_assistant_memory FROM PUBLIC, anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.restaurant_supplier_invoices FROM PUBLIC, anon, authenticated;

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.restaurant_assistant_conversations TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.restaurant_assistant_messages TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.restaurant_assistant_memory TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.restaurant_supplier_invoices TO authenticated;

CREATE POLICY restaurant_assistant_conversations_owner_all
ON public.restaurant_assistant_conversations
FOR ALL TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.restaurant_staff rs
        WHERE rs.restaurant_id = restaurant_assistant_conversations.restaurant_id
          AND rs.user_id = (SELECT auth.uid())
          AND rs.role = 'owner'
    )
)
WITH CHECK (
    created_by = (SELECT auth.uid())
    AND EXISTS (
        SELECT 1 FROM public.restaurant_staff rs
        WHERE rs.restaurant_id = restaurant_assistant_conversations.restaurant_id
          AND rs.user_id = (SELECT auth.uid())
          AND rs.role = 'owner'
    )
);

CREATE POLICY restaurant_assistant_messages_owner_all
ON public.restaurant_assistant_messages
FOR ALL TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.restaurant_staff rs
        WHERE rs.restaurant_id = restaurant_assistant_messages.restaurant_id
          AND rs.user_id = (SELECT auth.uid())
          AND rs.role = 'owner'
    )
)
WITH CHECK (
    created_by = (SELECT auth.uid())
    AND EXISTS (
        SELECT 1 FROM public.restaurant_staff rs
        WHERE rs.restaurant_id = restaurant_assistant_messages.restaurant_id
          AND rs.user_id = (SELECT auth.uid())
          AND rs.role = 'owner'
    )
);

CREATE POLICY restaurant_assistant_memory_owner_all
ON public.restaurant_assistant_memory
FOR ALL TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.restaurant_staff rs
        WHERE rs.restaurant_id = restaurant_assistant_memory.restaurant_id
          AND rs.user_id = (SELECT auth.uid())
          AND rs.role = 'owner'
    )
)
WITH CHECK (
    updated_by = (SELECT auth.uid())
    AND EXISTS (
        SELECT 1 FROM public.restaurant_staff rs
        WHERE rs.restaurant_id = restaurant_assistant_memory.restaurant_id
          AND rs.user_id = (SELECT auth.uid())
          AND rs.role = 'owner'
    )
);

CREATE POLICY restaurant_supplier_invoices_owner_select
ON public.restaurant_supplier_invoices
FOR SELECT TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.restaurant_staff rs
        WHERE rs.restaurant_id = restaurant_supplier_invoices.restaurant_id
          AND rs.user_id = (SELECT auth.uid())
          AND rs.role = 'owner'
    )
);

CREATE POLICY restaurant_supplier_invoices_owner_insert
ON public.restaurant_supplier_invoices
FOR INSERT TO authenticated
WITH CHECK (
    created_by = (SELECT auth.uid())
    AND EXISTS (
        SELECT 1 FROM public.restaurant_staff rs
        WHERE rs.restaurant_id = restaurant_supplier_invoices.restaurant_id
          AND rs.user_id = (SELECT auth.uid())
          AND rs.role = 'owner'
    )
);

CREATE POLICY restaurant_supplier_invoices_owner_update
ON public.restaurant_supplier_invoices
FOR UPDATE TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.restaurant_staff rs
        WHERE rs.restaurant_id = restaurant_supplier_invoices.restaurant_id
          AND rs.user_id = (SELECT auth.uid())
          AND rs.role = 'owner'
    )
)
WITH CHECK (
    EXISTS (
        SELECT 1 FROM public.restaurant_staff rs
        WHERE rs.restaurant_id = restaurant_supplier_invoices.restaurant_id
          AND rs.user_id = (SELECT auth.uid())
          AND rs.role = 'owner'
    )
);

CREATE POLICY restaurant_supplier_invoices_owner_delete
ON public.restaurant_supplier_invoices
FOR DELETE TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.restaurant_staff rs
        WHERE rs.restaurant_id = restaurant_supplier_invoices.restaurant_id
          AND rs.user_id = (SELECT auth.uid())
          AND rs.role = 'owner'
    )
);

CREATE OR REPLACE FUNCTION public.review_supplier_invoice(
    p_invoice_id uuid,
    p_review_status text
)
RETURNS uuid
LANGUAGE plpgsql
SET search_path = ''
AS $function$
DECLARE
    v_restaurant_id uuid;
BEGIN
    IF (SELECT auth.uid()) IS NULL THEN
        RAISE EXCEPTION 'É necessário iniciar sessão.';
    END IF;
    IF p_review_status NOT IN ('approved', 'rejected') THEN
        RAISE EXCEPTION 'Estado de revisão inválido.';
    END IF;

    UPDATE public.restaurant_supplier_invoices AS invoice
       SET review_status = p_review_status,
           reviewed_by = (SELECT auth.uid()),
           reviewed_at = now(),
           updated_at = now()
     WHERE invoice.id = p_invoice_id
       AND EXISTS (
           SELECT 1 FROM public.restaurant_staff rs
           WHERE rs.restaurant_id = invoice.restaurant_id
             AND rs.user_id = (SELECT auth.uid())
             AND rs.role = 'owner'
       )
    RETURNING invoice.restaurant_id INTO v_restaurant_id;

    IF v_restaurant_id IS NULL THEN
        RAISE EXCEPTION 'Fatura não encontrada ou sem autorização para a rever.';
    END IF;
    RETURN p_invoice_id;
END;
$function$;

REVOKE ALL ON FUNCTION public.review_supplier_invoice(uuid, text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.review_supplier_invoice(uuid, text) TO authenticated;

-- The CI PostgreSQL replay has no Storage extension. Create a private bucket and
-- owner-folder policies only where Supabase Storage exists (hosted deployment).
DO $migration$
BEGIN
    IF to_regclass('storage.buckets') IS NOT NULL THEN
        EXECUTE $sql$
            INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
            VALUES (
                'studiomesa-invoices',
                'studiomesa-invoices',
                false,
                26214400,
                ARRAY['application/pdf', 'image/jpeg', 'image/png', 'image/webp']::text[]
            )
            ON CONFLICT (id) DO NOTHING
        $sql$;
    END IF;

    IF to_regclass('storage.objects') IS NOT NULL THEN
        EXECUTE $sql$
            CREATE POLICY studiomesa_invoice_objects_owner_insert
            ON storage.objects
            FOR INSERT TO authenticated
            WITH CHECK (
                bucket_id = 'studiomesa-invoices'
                AND EXISTS (
                    SELECT 1 FROM public.restaurant_staff rs
                    WHERE rs.restaurant_id::text = (storage.foldername(name))[1]
                      AND rs.user_id = (SELECT auth.uid())
                      AND rs.role = 'owner'
                )
            )
        $sql$;

        EXECUTE $sql$
            CREATE POLICY studiomesa_invoice_objects_owner_select
            ON storage.objects
            FOR SELECT TO authenticated
            USING (
                bucket_id = 'studiomesa-invoices'
                AND EXISTS (
                    SELECT 1 FROM public.restaurant_staff rs
                    WHERE rs.restaurant_id::text = (storage.foldername(name))[1]
                      AND rs.user_id = (SELECT auth.uid())
                      AND rs.role = 'owner'
                )
            )
        $sql$;

        EXECUTE $sql$
            CREATE POLICY studiomesa_invoice_objects_owner_delete
            ON storage.objects
            FOR DELETE TO authenticated
            USING (
                bucket_id = 'studiomesa-invoices'
                AND EXISTS (
                    SELECT 1 FROM public.restaurant_staff rs
                    WHERE rs.restaurant_id::text = (storage.foldername(name))[1]
                      AND rs.user_id = (SELECT auth.uid())
                      AND rs.role = 'owner'
                )
            )
        $sql$;
    END IF;
END
$migration$;

COMMIT;
