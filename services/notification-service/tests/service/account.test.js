describe('getAccount', () => {
    const originalEnv = process.env;
    const originalFetch = global.fetch;
    let getAccount;

    const response = (status, body) => ({
        status,
        ok: status >= 200 && status < 300,
        json: jest.fn().mockResolvedValue(body),
    });

    const token = (value = 'token-1', expiresIn = 300) => response(200, { access_token: value, expires_in: expiresIn });

    const user = response(200, {
        id: 'user-1',
        username: 'jane.doe',
        email: 'jane@example.com',
        firstName: 'Jane',
        lastName: 'Doe',
        enabled: true,
    });

    beforeEach(() => {
        process.env = {
            ...originalEnv,
            KEYCLOAK_URL: 'http://keycloak:8080/',
            KEYCLOAK_REALM: 'ecommerce',
            KEYCLOAK_CLIENT_ID: 'notification-service',
            KEYCLOAK_CLIENT_SECRET: 'secret',
        };
        global.fetch = jest.fn();
        // A fresh module per test, so no token is cached from the one before.
        jest.isolateModules(() => {
            ({ getAccount } = require('../../src/service/account'));
        });
    });

    afterEach(() => {
        jest.useRealTimers();
    });

    afterAll(() => {
        process.env = originalEnv;
        global.fetch = originalFetch;
    });

    it('gets a service-account token, then the user', async () => {
        global.fetch.mockResolvedValueOnce(token()).mockResolvedValueOnce(user);

        await expect(getAccount('user-1')).resolves.toEqual({
            id: 'user-1',
            username: 'jane.doe',
            email: 'jane@example.com',
            firstName: 'Jane',
            lastName: 'Doe',
        });

        const [tokenUrl, tokenRequest] = global.fetch.mock.calls[0];
        expect(tokenUrl).toBe('http://keycloak:8080/realms/ecommerce/protocol/openid-connect/token');
        expect(tokenRequest.method).toBe('POST');
        expect(Object.fromEntries(tokenRequest.body)).toEqual({
            grant_type: 'client_credentials',
            client_id: 'notification-service',
            client_secret: 'secret',
        });

        const [userUrl, userRequest] = global.fetch.mock.calls[1];
        expect(userUrl).toBe('http://keycloak:8080/admin/realms/ecommerce/users/user-1');
        expect(userRequest.headers.Authorization).toBe('Bearer token-1');
    });

    it('escapes the user id in the URL', async () => {
        global.fetch.mockResolvedValueOnce(token()).mockResolvedValueOnce(user);

        await getAccount('../clients');

        expect(global.fetch.mock.calls[1][0]).toBe('http://keycloak:8080/admin/realms/ecommerce/users/..%2Fclients');
    });

    it('reuses the token until shortly before it expires', async () => {
        jest.useFakeTimers({ now: 0 });
        global.fetch
            .mockResolvedValueOnce(token('token-1', 300))
            .mockResolvedValueOnce(user)
            .mockResolvedValueOnce(user)
            .mockResolvedValueOnce(token('token-2', 300))
            .mockResolvedValueOnce(user);

        await getAccount('user-1');
        jest.setSystemTime(269 * 1000);
        await getAccount('user-1');
        // Inside the 30 second margin before expiry.
        jest.setSystemTime(271 * 1000);
        await getAccount('user-1');

        expect(global.fetch).toHaveBeenCalledTimes(5);
        expect(global.fetch.mock.calls[2][1].headers.Authorization).toBe('Bearer token-1');
        expect(global.fetch.mock.calls[4][1].headers.Authorization).toBe('Bearer token-2');
    });

    it('gets a new token once when Keycloak rejects the cached one', async () => {
        global.fetch
            .mockResolvedValueOnce(token('revoked'))
            .mockResolvedValueOnce(response(401))
            .mockResolvedValueOnce(token('token-2'))
            .mockResolvedValueOnce(user);

        await expect(getAccount('user-1')).resolves.toMatchObject({ email: 'jane@example.com' });
        expect(global.fetch.mock.calls[3][1].headers.Authorization).toBe('Bearer token-2');
    });

    it('resolves to null for a user that does not exist', async () => {
        global.fetch.mockResolvedValueOnce(token()).mockResolvedValueOnce(response(404));

        await expect(getAccount('user-1')).resolves.toBeNull();
    });

    it('fails when the user lookup fails', async () => {
        global.fetch.mockResolvedValueOnce(token()).mockResolvedValueOnce(response(503));

        await expect(getAccount('user-1')).rejects.toThrow('Keycloak user lookup for user-1 failed with status 503');
    });

    it('fails when the lookup is still unauthorized with a fresh token', async () => {
        global.fetch
            .mockResolvedValueOnce(token())
            .mockResolvedValueOnce(response(401))
            .mockResolvedValueOnce(token())
            .mockResolvedValueOnce(response(401));

        await expect(getAccount('user-1')).rejects.toThrow('failed with status 401');
    });

    it('fails when no token can be had', async () => {
        global.fetch.mockResolvedValueOnce(response(401));

        await expect(getAccount('user-1')).rejects.toThrow('Keycloak token request failed with status 401');
        expect(global.fetch).toHaveBeenCalledTimes(1);
    });
});
