-- P2-A1: premium (Pro) lessons. Free users can still list/open a Pro lesson and read its
-- transcript; practice-service blocks Dictation/Shadowing attempts on it unless the user is
-- premium (or an admin). Existing lessons stay free.
ALTER TABLE lesson ADD COLUMN is_pro boolean NOT NULL DEFAULT false;
