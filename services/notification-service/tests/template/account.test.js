const templates = require('../../src/template/account');

const baseEvent = {
    firstName: 'Jane',
    username: 'jane.doe',
    occurredAt: '2026-09-30T10:00:00Z',
    data: {},
};

describe('account templates', () => {
    it('exposes a template for every auth-service event type', () => {
        expect(Object.keys(templates).sort()).toEqual([
            'ACCOUNT_DELETED',
            'ACCOUNT_REGISTERED',
            'ACCOUNT_UPDATED',
            'PASSWORD_CHANGED',
        ]);
    });

    describe('ACCOUNT_REGISTERED', () => {
        it('includes the username and temporary password', () => {
            const { subject, text } = templates.ACCOUNT_REGISTERED({
                ...baseEvent,
                data: { temporaryPassword: 'S3cr3t!' },
            });

            expect(subject).toBe('Your ecommerce account');
            expect(text).toContain('Hello Jane,');
            expect(text).toContain('Username:  jane.doe');
            expect(text).toContain('Password:  S3cr3t!');
            expect(text).toMatch(/Regards,\nEcommerce Team$/);
        });
    });

    describe('ACCOUNT_UPDATED', () => {
        it('lists changed fields using their readable labels', () => {
            const { subject, text } = templates.ACCOUNT_UPDATED({
                ...baseEvent,
                data: { changedFields: ['email', 'firstName', 'lastName'] },
            });

            expect(subject).toBe('Your ecommerce account was updated');
            expect(text).toContain('(jane.doe) were changed on 2026-09-30T10:00:00Z');
            expect(text).toContain('    - email address\n    - first name\n    - last name');
        });

        it('falls back to the raw field name when it has no label', () => {
            const { text } = templates.ACCOUNT_UPDATED({
                ...baseEvent,
                data: { changedFields: ['phoneNumber'] },
            });

            expect(text).toContain('    - phoneNumber');
        });

        it('handles a missing changedFields list', () => {
            const { text } = templates.ACCOUNT_UPDATED(baseEvent);

            expect(text).not.toContain('    - ');
            expect(text).toContain('If you did not make this change');
        });
    });

    describe('PASSWORD_CHANGED', () => {
        it('mentions the account and when it changed', () => {
            const { subject, text } = templates.PASSWORD_CHANGED(baseEvent);

            expect(subject).toBe('Your ecommerce password was changed');
            expect(text).toContain('The password of your account (jane.doe) was changed on 2026-09-30T10:00:00Z.');
        });
    });

    describe('ACCOUNT_DELETED', () => {
        it('mentions the account and when it was deleted', () => {
            const { subject, text } = templates.ACCOUNT_DELETED(baseEvent);

            expect(subject).toBe('Your ecommerce account was deleted');
            expect(text).toContain('Your account (jane.doe) was deleted on 2026-09-30T10:00:00Z.');
        });
    });
});
