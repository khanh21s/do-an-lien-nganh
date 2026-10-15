-- P2-C: several personal decks per user, named/ordered groups inside any deck.
-- Forward-only; V1 = baseline, V2 = gamification (another branch), V3 = this file.

-- 1) Personal decks. Until now a user owned exactly one personal deck (the notebook,
--    "Sổ tay cá nhân", unique index ux_vocab_deck_owner). The notebook keeps its special
--    behaviour (every card has a user_vocab row, due at once) and is now marked explicitly;
--    other personal decks are ordinary decks that only their owner can see.
ALTER TABLE vocab_deck ADD COLUMN is_notebook boolean NOT NULL DEFAULT false;
UPDATE vocab_deck SET is_notebook = true WHERE owner_id IS NOT NULL;

DROP INDEX IF EXISTS ux_vocab_deck_owner;
-- At most one notebook per user (the lazy-create INSERT ... ON CONFLICT relies on it).
CREATE UNIQUE INDEX ux_vocab_deck_notebook ON vocab_deck (owner_id) WHERE is_notebook;
CREATE INDEX idx_vocab_deck_owner ON vocab_deck (owner_id, created_at) WHERE owner_id IS NOT NULL;
ALTER TABLE vocab_deck ADD CONSTRAINT ck_vocab_deck_notebook_owner CHECK (NOT is_notebook OR owner_id IS NOT NULL);

-- 2) Groups inside a deck (public or personal). Deleting a deck deletes its groups;
--    deleting a group keeps its cards (they become ungrouped).
CREATE TABLE vocab_group (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    deck_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT vocab_group_pkey PRIMARY KEY (id),
    CONSTRAINT vocab_group_deck_id_fkey FOREIGN KEY (deck_id) REFERENCES vocab_deck (id) ON DELETE CASCADE
);
CREATE INDEX idx_vocab_group_deck ON vocab_group (deck_id, sort_order);
CREATE UNIQUE INDEX ux_vocab_group_deck_name ON vocab_group (deck_id, lower(name));
CREATE TRIGGER update_vocab_group_updated_at BEFORE UPDATE ON vocab_group
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE vocab_card ADD COLUMN group_id uuid;
ALTER TABLE vocab_card ADD CONSTRAINT vocab_card_group_id_fkey
    FOREIGN KEY (group_id) REFERENCES vocab_group (id) ON DELETE SET NULL;
CREATE INDEX idx_vocab_card_group ON vocab_card (group_id) WHERE group_id IS NOT NULL;
-- Duplicate checks (import, add card) compare lower(trim(word)) within a deck.
CREATE INDEX idx_vocab_card_deck_lword ON vocab_card (deck_id, lower(btrim(word)));
