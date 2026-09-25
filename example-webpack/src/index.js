import { Schema, Table, PowerSyncDatabase, column, createConsoleLogger, LogLevels } from '@powersync/web';

//
// open a WebSocket back to the Jepsen control node
//

// webapp was started with URL params ?myHostname=...&jepsenControlNode=...
const urlSearchParams = new URLSearchParams(document.location.search);
const myHostname = urlSearchParams.get("myHostname");
const jepsenControlNode = urlSearchParams.get("jepsenControlNode");

// websocket and handlers
let jepsenWebsocket;
try {
  jepsenWebsocket = new WebSocket("ws://" + jepsenControlNode + ":8090");
  jepsenWebsocket.addEventListener("open", () => {
    console.log(`${myHostname}: open: connected to ${jepsenControlNode}`);
  });
  jepsenWebsocket.addEventListener("message", (e) => {
    console.log(`${myHostname}: message: ${JSON.parse(e)}`);
  });
  jepsenWebsocket.addEventListener("close", () => {
    console.log(`${myHostname}: close: disconnected from ${jepsenControlNode}`);
  });
  jepsenWebsocket.addEventListener("error", (e) => {
    console.error(`${myHostname}: error: ${e}`);
    throw new Error(`${e}`);
  });

  // "register" with the Jepsen control node
  jepsenWebsocket.send(JSON.stringify({ "type": "invoke", "f": "register", "value": myHostname }));
} catch (e) {
  console.error(`${myHostname}: error creating WebSocket: ${e}`);
  throw new Error(`${e}`);
}

//
// PowerSync
//

const logger = createConsoleLogger({ minLevel: LogLevels.debug });

/**
 * A placeholder connector which doesn't do anything.
 * This is just used to verify that the sync workers can be loaded
 * when connecting.
 */
class DummyConnector {
  async fetchCredentials() {
    return {
      endpoint: '',
      token: ''
    };
  }

  async uploadData(database) { }
}

const customers = new Table({ name: column.text });

export const AppSchema = new Schema({ customers });

let PowerSync;

const openDatabase = async () => {
  PowerSync = new PowerSyncDatabase({
    schema: AppSchema,
    database: { dbFilename: 'test.sqlite' },
    logger
  });

  await PowerSync.init();

  // Run local statements.
  await PowerSync.execute('INSERT INTO customers(id, name) VALUES(uuid(), ?)', ['Fred']);

  const result = await PowerSync.getAll('SELECT * FROM customers');
  console.log('contents of customers: ', result);

  console.log(
    `Attempting to connect in order to verify web workers are correctly loaded.
    This doesn't use any actual network credentials.
    Network errors will be shown: these can be ignored.`
  );

  /**
   * Try and connect, this will setup shared sync workers
   * This will fail due to not having a valid endpoint,
   * but it will try - which is all that matters.
   */
  await PowerSync.connect(new DummyConnector());
};

document.addEventListener('DOMContentLoaded', (event) => {
  openDatabase();
});
