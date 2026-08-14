CREATE TABLE analytics.click_summary (
    short_code       VARCHAR(20) PRIMARY KEY,
    total_clicks     BIGINT NOT NULL DEFAULT 0,
    last_clicked_at  TIMESTAMPTZ
);

CREATE TABLE analytics.click_daily_rollup (
    short_code    VARCHAR(20) NOT NULL,
    click_date    DATE NOT NULL,
    click_count   BIGINT NOT NULL DEFAULT 0,
    top_referrer  VARCHAR(255),
    geo_region    VARCHAR(10),
    PRIMARY KEY (short_code, click_date)
);

CREATE TABLE analytics.click_events_dlq (
    id             BIGSERIAL PRIMARY KEY,
    short_code     VARCHAR(20) NOT NULL,
    payload        JSONB NOT NULL,
    failure_reason TEXT,
    failed_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
