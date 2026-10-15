-- =============================================================================
-- content-service / content_db - Flyway BASELINE (version 1). Reference only.
--
-- Exact schema of content_db as produced by init-db.sql + initdb/*.sh seeds +
-- scripts/migrations/001-007, generated from the running database with
--   pg_dump --schema-only --no-owner --no-privileges content_db
-- (SET/session lines stripped; schema only, no seed data).
--
-- This file is NOT executed on existing or fresh docker volumes: init-db.sql has
-- already created these tables, so spring.flyway.baseline-on-migrate=true records
-- version 1 as "<< Flyway Baseline >>" in flyway_schema_history and skips it.
-- It only runs against a completely EMPTY database. Never edit it; every schema
-- change is a new file V<n>__<description>.sql next to this one (README
-- "Operations notes").
-- =============================================================================

--
-- Name: update_updated_at_column(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_updated_at_column() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.updated_at = CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$$;

--
-- Name: lesson; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lesson (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    title character varying(255) NOT NULL,
    source_url text,
    cefr_level character varying(2),
    topic_tag character varying(100),
    audio_url text NOT NULL,
    description text,
    duration integer,
    is_deleted boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

--
-- Name: sentence; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sentence (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    lesson_id uuid NOT NULL,
    sentence_order integer NOT NULL,
    text text NOT NULL,
    ipa character varying(255),
    translation_vi text,
    start_ms integer,
    end_ms integer,
    audio_url text,
    image_url text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

--
-- Name: lesson lesson_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lesson
    ADD CONSTRAINT lesson_pkey PRIMARY KEY (id);

--
-- Name: sentence sentence_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sentence
    ADD CONSTRAINT sentence_pkey PRIMARY KEY (id);

--
-- Name: idx_lesson_cefr_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lesson_cefr_level ON public.lesson USING btree (cefr_level);

--
-- Name: idx_lesson_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lesson_created_at ON public.lesson USING btree (created_at DESC);

--
-- Name: idx_lesson_topic_tag; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lesson_topic_tag ON public.lesson USING btree (topic_tag);

--
-- Name: idx_sentence_lesson_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sentence_lesson_id ON public.sentence USING btree (lesson_id);

--
-- Name: idx_sentence_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sentence_order ON public.sentence USING btree (lesson_id, sentence_order);

--
-- Name: lesson update_lesson_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_lesson_updated_at BEFORE UPDATE ON public.lesson FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

--
-- Name: sentence sentence_lesson_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sentence
    ADD CONSTRAINT sentence_lesson_id_fkey FOREIGN KEY (lesson_id) REFERENCES public.lesson(id) ON DELETE CASCADE;
