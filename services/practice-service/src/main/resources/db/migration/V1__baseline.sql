-- =============================================================================
-- practice-service / practice_db - Flyway BASELINE (version 1). Reference only.
--
-- Exact schema of practice_db as produced by init-db.sql + initdb/*.sh seeds +
-- scripts/migrations/001-007, generated from the running database with
--   pg_dump --schema-only --no-owner --no-privileges practice_db
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
-- Name: goal_day; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.goal_day (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    day date NOT NULL,
    source character varying(20) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

--
-- Name: practice_attempt; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.practice_attempt (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    lesson_id uuid NOT NULL,
    sentence_id uuid,
    mode character varying(20) NOT NULL,
    score numeric,
    accuracy numeric,
    word_scores jsonb,
    audio_recording_url text,
    duration numeric,
    difficulty character varying(20),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

--
-- Name: goal_day goal_day_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goal_day
    ADD CONSTRAINT goal_day_pkey PRIMARY KEY (id);

--
-- Name: goal_day goal_day_user_id_day_source_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goal_day
    ADD CONSTRAINT goal_day_user_id_day_source_key UNIQUE (user_id, day, source);

--
-- Name: practice_attempt practice_attempt_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.practice_attempt
    ADD CONSTRAINT practice_attempt_pkey PRIMARY KEY (id);

--
-- Name: idx_practice_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_practice_created_at ON public.practice_attempt USING btree (created_at DESC);

--
-- Name: idx_practice_lesson_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_practice_lesson_id ON public.practice_attempt USING btree (lesson_id);

--
-- Name: idx_practice_mode; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_practice_mode ON public.practice_attempt USING btree (mode);

--
-- Name: idx_practice_sentence_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_practice_sentence_id ON public.practice_attempt USING btree (sentence_id);

--
-- Name: idx_practice_user_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_practice_user_created ON public.practice_attempt USING btree (user_id, created_at DESC);

--
-- Name: idx_practice_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_practice_user_id ON public.practice_attempt USING btree (user_id);
