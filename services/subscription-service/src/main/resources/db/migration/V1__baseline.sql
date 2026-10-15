-- =============================================================================
-- subscription-service / subscription_db - Flyway BASELINE (version 1). Reference only.
--
-- Exact schema of subscription_db as produced by init-db.sql + initdb/*.sh seeds +
-- scripts/migrations/001-007, generated from the running database with
--   pg_dump --schema-only --no-owner --no-privileges subscription_db
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
-- Name: activation_code; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.activation_code (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(50) NOT NULL,
    plan_id character varying(50) NOT NULL,
    used_by_user_id uuid,
    used_at timestamp with time zone,
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

--
-- Name: payment_transaction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_transaction (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    plan_id character varying(50),
    amount bigint NOT NULL,
    method character varying(50),
    status character varying(20) DEFAULT 'pending'::character varying,
    transaction_id character varying(255),
    description text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

--
-- Name: plan; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.plan (
    id character varying(50) NOT NULL,
    name character varying(255) NOT NULL,
    duration_days integer NOT NULL,
    price bigint NOT NULL,
    features jsonb,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    is_active boolean DEFAULT true NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

--
-- Name: subscription; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscription (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    plan_id character varying(50) NOT NULL,
    start_at timestamp with time zone NOT NULL,
    end_at timestamp with time zone NOT NULL,
    status character varying(20) DEFAULT 'active'::character varying,
    payment_method character varying(50),
    transaction_id character varying(255),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

--
-- Name: activation_code activation_code_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activation_code
    ADD CONSTRAINT activation_code_code_key UNIQUE (code);

--
-- Name: activation_code activation_code_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activation_code
    ADD CONSTRAINT activation_code_pkey PRIMARY KEY (id);

--
-- Name: payment_transaction payment_transaction_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_transaction
    ADD CONSTRAINT payment_transaction_pkey PRIMARY KEY (id);

--
-- Name: payment_transaction payment_transaction_transaction_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_transaction
    ADD CONSTRAINT payment_transaction_transaction_id_key UNIQUE (transaction_id);

--
-- Name: plan plan_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan
    ADD CONSTRAINT plan_pkey PRIMARY KEY (id);

--
-- Name: subscription subscription_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription
    ADD CONSTRAINT subscription_pkey PRIMARY KEY (id);

--
-- Name: idx_activation_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_activation_code ON public.activation_code USING btree (code);

--
-- Name: idx_activation_code_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_activation_code_expires ON public.activation_code USING btree (expires_at);

--
-- Name: idx_payment_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_created ON public.payment_transaction USING btree (created_at DESC);

--
-- Name: idx_payment_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_status ON public.payment_transaction USING btree (status);

--
-- Name: idx_payment_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_user ON public.payment_transaction USING btree (user_id);

--
-- Name: idx_plan_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_plan_active ON public.plan USING btree (created_at DESC);

--
-- Name: idx_subscription_end_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_subscription_end_at ON public.subscription USING btree (end_at) WHERE ((status)::text = 'active'::text);

--
-- Name: idx_subscription_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_subscription_status ON public.subscription USING btree (status);

--
-- Name: idx_subscription_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_subscription_user ON public.subscription USING btree (user_id);

--
-- Name: subscription update_subscription_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_subscription_updated_at BEFORE UPDATE ON public.subscription FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

--
-- Name: activation_code activation_code_plan_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activation_code
    ADD CONSTRAINT activation_code_plan_id_fkey FOREIGN KEY (plan_id) REFERENCES public.plan(id);

--
-- Name: subscription subscription_plan_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription
    ADD CONSTRAINT subscription_plan_id_fkey FOREIGN KEY (plan_id) REFERENCES public.plan(id);
