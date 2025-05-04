import { withMiddlewareAuthRequired } from '@auth0/nextjs-auth0/next';

export default withMiddlewareAuthRequired();

export const config = {
    matcher: ['/dashboard/:path*'],
};
