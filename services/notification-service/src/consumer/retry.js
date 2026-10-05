const { UnprocessableEventError } = require('../handler/error');

// A message worth a second try: not malformed, not unprocessable, and not
// already on its second delivery.
const shouldRetry = (error, msg) => !(error instanceof SyntaxError || error instanceof UnprocessableEventError)
    && !msg.fields.redelivered;

module.exports = { shouldRetry };
