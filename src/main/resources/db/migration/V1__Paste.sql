CREATE TABLE paste (
  id                VARCHAR(16) NOT NULL PRIMARY KEY,
  ownerToken        VARCHAR(64) NOT NULL,

  inputCode         TEXT        NOT NULL,
  outputCompilerLog TEXT        NOT NULL,
  outputJavap       TEXT
)