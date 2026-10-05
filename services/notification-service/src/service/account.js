// Looks customers up in Keycloak, which owns their email address and name. The
// order service only knows a customer by the id in their token.

// Refreshed a little before Keycloak would stop accepting it.
const TOKEN_EXPIRY_MARGIN_MS = 30 * 1000;

let cachedToken = null;

const keycloakUrl = () => process.env.KEYCLOAK_URL.replace(/\/+$/, '');

const requestToken = async () => {
    const response = await fetch(
        `${keycloakUrl()}/realms/${process.env.KEYCLOAK_REALM}/protocol/openid-connect/token`,
        {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({
                grant_type: 'client_credentials',
                client_id: process.env.KEYCLOAK_CLIENT_ID,
                client_secret: process.env.KEYCLOAK_CLIENT_SECRET,
            }),
        },
    );

    if (!response.ok) {
        throw new Error(`Keycloak token request failed with status ${response.status}`);
    }

    const body = await response.json();
    cachedToken = {
        value: body.access_token,
        expiresAt: Date.now() + body.expires_in * 1000 - TOKEN_EXPIRY_MARGIN_MS,
    };
    return cachedToken.value;
};

const getToken = async () => {
    if (cachedToken && cachedToken.expiresAt > Date.now()) {
        return cachedToken.value;
    }
    return requestToken();
};

const fetchUser = (userId, token) => fetch(
    `${keycloakUrl()}/admin/realms/${process.env.KEYCLOAK_REALM}/users/${encodeURIComponent(userId)}`,
    { headers: { Authorization: `Bearer ${token}` } },
);

// Resolves to null when the user no longer exists, so the caller can tell a
// deleted customer from Keycloak being unreachable.
const getAccount = async (userId) => {
    let response = await fetchUser(userId, await getToken());

    // A token revoked before it expired: get a fresh one and try once more.
    if (response.status === 401) {
        cachedToken = null;
        response = await fetchUser(userId, await getToken());
    }

    if (response.status === 404) {
        return null;
    }

    if (!response.ok) {
        throw new Error(`Keycloak user lookup for ${userId} failed with status ${response.status}`);
    }

    const user = await response.json();
    return {
        id: user.id,
        username: user.username,
        email: user.email,
        firstName: user.firstName,
        lastName: user.lastName,
    };
};

module.exports = { getAccount };
