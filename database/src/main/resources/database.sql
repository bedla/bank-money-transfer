CREATE TABLE account (
  id INTEGER AUTO_INCREMENT PRIMARY KEY,
  type VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  date_opened TIMESTAMP (9) WITH TIME ZONE,
  balance DECIMAL NOT NULL,
  version INTEGER NOT NULL
);

CREATE TABLE waiting_room (
  id INTEGER AUTO_INCREMENT PRIMARY KEY,
  from_acc_id INTEGER NOT NULL,
  to_acc_id INTEGER NOT NULL,
  amount DECIMAL NOT NULL,
  state VARCHAR(32) NOT NULL,
  date_created TIMESTAMP (9) WITH TIME ZONE,
  version INTEGER NOT NULL,
  FOREIGN KEY (from_acc_id) references account(id),
  FOREIGN KEY (to_acc_id) references account(id),
);

CREATE TABLE transaction (
  wr_id INTEGER NOT NULL,
  from_acc_id INTEGER NOT NULL,
  to_acc_id INTEGER NOT NULL,
  amount DECIMAL NOT NULL,
  date_transacted TIMESTAMP (9) WITH TIME ZONE,
  FOREIGN KEY (from_acc_id) references account(id),
  FOREIGN KEY (to_acc_id) references account(id),
  FOREIGN KEY (wr_id) references waiting_room(id),
  PRIMARY KEY (wr_id)
);
