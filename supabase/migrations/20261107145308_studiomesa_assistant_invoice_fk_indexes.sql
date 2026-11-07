-- Cover the foreign keys of the owner-only assistant and supplier invoice tables.

BEGIN;

CREATE INDEX IF NOT EXISTS restaurant_assistant_conversations_created_by_idx
    ON public.restaurant_assistant_conversations (created_by);

CREATE INDEX IF NOT EXISTS restaurant_assistant_messages_conversation_restaurant_idx
    ON public.restaurant_assistant_messages (conversation_id, restaurant_id);

CREATE INDEX IF NOT EXISTS restaurant_assistant_messages_restaurant_id_idx
    ON public.restaurant_assistant_messages (restaurant_id);

CREATE INDEX IF NOT EXISTS restaurant_assistant_messages_created_by_idx
    ON public.restaurant_assistant_messages (created_by);

CREATE INDEX IF NOT EXISTS restaurant_assistant_memory_updated_by_idx
    ON public.restaurant_assistant_memory (updated_by);

CREATE INDEX IF NOT EXISTS restaurant_supplier_invoices_created_by_idx
    ON public.restaurant_supplier_invoices (created_by);

CREATE INDEX IF NOT EXISTS restaurant_supplier_invoices_reviewed_by_idx
    ON public.restaurant_supplier_invoices (reviewed_by);

COMMIT;
