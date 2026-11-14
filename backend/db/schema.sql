-- RoadPulse backend schema (PostgreSQL + PostGIS). Phase 2/3 API services build on this.
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name  text NOT NULL,
    email         text UNIQUE,
    points        integer NOT NULL DEFAULT 0,
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TYPE report_type AS ENUM ('POLICE', 'CRASH', 'HAZARD', 'TRAFFIC_JAM', 'ROAD_CLOSED');

CREATE TABLE reports (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid REFERENCES users(id) ON DELETE SET NULL,
    type           report_type NOT NULL,
    location       geography(Point, 4326) NOT NULL,
    heading_deg    smallint,
    confirmations  integer NOT NULL DEFAULT 0,
    dismissals     integer NOT NULL DEFAULT 0,
    created_at     timestamptz NOT NULL DEFAULT now(),
    expires_at     timestamptz NOT NULL
);
CREATE INDEX reports_location_idx ON reports USING gist (location);
CREATE INDEX reports_expires_idx ON reports (expires_at);

CREATE TABLE report_votes (
    report_id  uuid NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    still_there boolean NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (report_id, user_id)
);

-- Anonymous speed samples from driving users, map-matched to road segments.
-- Aggregated into per-segment speeds that feed Valhalla's live traffic tiles.
CREATE TABLE speed_samples (
    id           bigserial PRIMARY KEY,
    way_id       bigint NOT NULL,
    speed_kph    real NOT NULL,
    heading_deg  smallint,
    recorded_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX speed_samples_way_time_idx ON speed_samples (way_id, recorded_at DESC);

CREATE TABLE friendships (
    user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    friend_id  uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    accepted   boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, friend_id)
);

-- Example: active reports within 2 km of a point
--   SELECT * FROM reports
--   WHERE expires_at > now()
--     AND ST_DWithin(location, ST_MakePoint(101.6869, 3.139)::geography, 2000);
