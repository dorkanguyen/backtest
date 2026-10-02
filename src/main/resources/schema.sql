CREATE TABLE IF NOT EXISTS candles (
  id        INTEGER PRIMARY KEY AUTOINCREMENT,
  symbol    TEXT    NOT NULL,
  open_time TEXT    NOT NULL,
  open      REAL    NOT NULL,
  high      REAL    NOT NULL,
  low       REAL    NOT NULL,
  close     REAL    NOT NULL,
  volume    INTEGER NOT NULL
);