-- =============================================================================
-- auth-service / auth_db - Flyway BASELINE (version 1). Reference only.
--
-- Exact schema of auth_db as produced by init-db.sql + initdb/*.sh seeds +
-- scripts/migrations/001-007, generated from the running database with
--   pg_dump --schema-only --no-owner --no-privileges auth_db
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
-- Name: user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."user" (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255),
    auth_provider character varying(50) DEFAULT 'email'::character varying NOT NULL,
    cefr_level character varying(2) DEFAULT 'B1'::character varying NOT NULL,
    total_hours_learned numeric DEFAULT 0,
    total_words_learned integer DEFAULT 0,
    avatar_url text,
    is_active boolean DEFAULT true,
    last_login_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

--
-- Name: user user_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."user"
    ADD CONSTRAINT user_email_key UNIQUE (email);

--
-- Name: user user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."user"
    ADD CONSTRAINT user_pkey PRIMARY KEY (id);

--
-- Name: idx_user_cefr_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_cefr_level ON public."user" USING btree (cefr_level);

--
-- Name: idx_user_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_created_at ON public."user" USING btree (created_at DESC);

--
-- Name: idx_user_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_email ON public."user" USING btree (email);

--
-- Name: idx_user_is_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_is_active ON public."user" USING btree (is_active);

--
-- Name: user update_user_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_user_updated_at BEFORE UPDATE ON public."user" FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
