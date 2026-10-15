-- =============================================================================
-- vocabulary-service / vocabulary_db - Flyway BASELINE (version 1). Reference only.
--
-- Exact schema of vocabulary_db as produced by init-db.sql + initdb/*.sh seeds +
-- scripts/migrations/001-007, generated from the running database with
--   pg_dump --schema-only --no-owner --no-privileges vocabulary_db
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
-- Name: daily_challenge; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.daily_challenge (
    day date NOT NULL,
    card_ids text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

--
-- Name: game_answer; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.game_answer (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    session_id uuid NOT NULL,
    seq integer NOT NULL,
    card_id uuid NOT NULL,
    is_retry boolean DEFAULT false NOT NULL,
    asked_at timestamp with time zone NOT NULL,
    answered_at timestamp with time zone,
    answer character varying(255),
    result character varying(10),
    hints_used integer DEFAULT 0 NOT NULL,
    points integer DEFAULT 0 NOT NULL
);

--
-- Name: game_badge; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.game_badge (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    code character varying(40) NOT NULL,
    session_id uuid,
    earned_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--
-- Name: game_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.game_profile (
    user_id uuid NOT NULL,
    xp bigint DEFAULT 0 NOT NULL,
    daily_goal integer DEFAULT 200 NOT NULL,
    goal_recorded_day date,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

--
-- Name: game_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.game_session (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    mode character varying(20) NOT NULL,
    source character varying(20) NOT NULL,
    source_key character varying(80) NOT NULL,
    source_label character varying(255) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    card_queue text DEFAULT ''::text NOT NULL,
    retry_queue text DEFAULT ''::text NOT NULL,
    score integer DEFAULT 0 NOT NULL,
    combo integer DEFAULT 0 NOT NULL,
    best_combo integer DEFAULT 0 NOT NULL,
    correct_count integer DEFAULT 0 NOT NULL,
    near_count integer DEFAULT 0 NOT NULL,
    wrong_count integer DEFAULT 0 NOT NULL,
    skipped_count integer DEFAULT 0 NOT NULL,
    previous_best integer,
    srs_updated integer DEFAULT 0 NOT NULL,
    started_at timestamp with time zone NOT NULL,
    ends_at timestamp with time zone NOT NULL,
    finished_at timestamp with time zone,
    lives integer,
    fast_count integer DEFAULT 0 NOT NULL,
    display_name character varying(50),
    play_date date NOT NULL,
    ranked boolean DEFAULT false NOT NULL
);

--
-- Name: user_vocab; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_vocab (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    card_id uuid NOT NULL,
    next_review_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    interval_days integer DEFAULT 1,
    ease_factor numeric DEFAULT 2.5,
    repetitions integer DEFAULT 0,
    quality integer,
    last_review_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

--
-- Name: vocab_card; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vocab_card (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    deck_id uuid NOT NULL,
    word character varying(255) NOT NULL,
    meaning text NOT NULL,
    example text,
    example_vi text,
    ipa character varying(255),
    audio_url text,
    image_url text,
    part_of_speech character varying(50),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

--
-- Name: vocab_deck; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vocab_deck (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    level character varying(2),
    is_pro_only boolean DEFAULT false,
    card_count integer DEFAULT 0,
    created_by uuid,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    owner_id uuid
);

--
-- Name: daily_challenge daily_challenge_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.daily_challenge
    ADD CONSTRAINT daily_challenge_pkey PRIMARY KEY (day);

--
-- Name: game_answer game_answer_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.game_answer
    ADD CONSTRAINT game_answer_pkey PRIMARY KEY (id);

--
-- Name: game_answer game_answer_session_id_seq_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.game_answer
    ADD CONSTRAINT game_answer_session_id_seq_key UNIQUE (session_id, seq);

--
-- Name: game_badge game_badge_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.game_badge
    ADD CONSTRAINT game_badge_pkey PRIMARY KEY (id);

--
-- Name: game_badge game_badge_user_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.game_badge
    ADD CONSTRAINT game_badge_user_id_code_key UNIQUE (user_id, code);

--
-- Name: game_profile game_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.game_profile
    ADD CONSTRAINT game_profile_pkey PRIMARY KEY (user_id);

--
-- Name: game_session game_session_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.game_session
    ADD CONSTRAINT game_session_pkey PRIMARY KEY (id);

--
-- Name: user_vocab user_vocab_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_vocab
    ADD CONSTRAINT user_vocab_pkey PRIMARY KEY (id);

--
-- Name: vocab_card vocab_card_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vocab_card
    ADD CONSTRAINT vocab_card_pkey PRIMARY KEY (id);

--
-- Name: vocab_deck vocab_deck_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vocab_deck
    ADD CONSTRAINT vocab_deck_pkey PRIMARY KEY (id);

--
-- Name: idx_game_session_best; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_game_session_best ON public.game_session USING btree (user_id, source_key, score DESC);

--
-- Name: idx_game_session_board; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_game_session_board ON public.game_session USING btree (mode, play_date, score DESC) WHERE (((status)::text = 'FINISHED'::text) AND ranked);

--
-- Name: idx_game_session_user_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_game_session_user_status ON public.game_session USING btree (user_id, status);

--
-- Name: idx_user_vocab_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_vocab_created ON public.user_vocab USING btree (user_id, created_at DESC);

--
-- Name: idx_user_vocab_next_review; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_vocab_next_review ON public.user_vocab USING btree (user_id, next_review_at);

--
-- Name: idx_user_vocab_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_vocab_user ON public.user_vocab USING btree (user_id);

--
-- Name: idx_user_vocab_user_card; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_user_vocab_user_card ON public.user_vocab USING btree (user_id, card_id);

--
-- Name: idx_vocab_card_deck; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vocab_card_deck ON public.vocab_card USING btree (deck_id);

--
-- Name: idx_vocab_card_word; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vocab_card_word ON public.vocab_card USING btree (word);

--
-- Name: idx_vocab_deck_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vocab_deck_level ON public.vocab_deck USING btree (level);

--
-- Name: ux_game_session_daily; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_game_session_daily ON public.game_session USING btree (user_id, source_key) WHERE ((mode)::text = 'DAILY'::text);

--
-- Name: ux_vocab_deck_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_vocab_deck_owner ON public.vocab_deck USING btree (owner_id) WHERE (owner_id IS NOT NULL);

--
-- Name: user_vocab update_user_vocab_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_user_vocab_updated_at BEFORE UPDATE ON public.user_vocab FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

--
-- Name: vocab_deck update_vocab_deck_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_vocab_deck_updated_at BEFORE UPDATE ON public.vocab_deck FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

--
-- Name: game_answer game_answer_card_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.game_answer
    ADD CONSTRAINT game_answer_card_id_fkey FOREIGN KEY (card_id) REFERENCES public.vocab_card(id) ON DELETE CASCADE;

--
-- Name: game_answer game_answer_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.game_answer
    ADD CONSTRAINT game_answer_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.game_session(id) ON DELETE CASCADE;

--
-- Name: user_vocab user_vocab_card_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_vocab
    ADD CONSTRAINT user_vocab_card_id_fkey FOREIGN KEY (card_id) REFERENCES public.vocab_card(id) ON DELETE CASCADE;

--
-- Name: vocab_card vocab_card_deck_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vocab_card
    ADD CONSTRAINT vocab_card_deck_id_fkey FOREIGN KEY (deck_id) REFERENCES public.vocab_deck(id) ON DELETE CASCADE;
