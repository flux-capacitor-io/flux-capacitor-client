import * as core from '@actions/core';
import * as jwt from 'jsonwebtoken';
import {ApiKeyResult} from "./ApiKeyResult";

async function run() {
    try {
        const apiKey: ApiKeyResult = JSON.parse(core.getInput('api-key', {required: true}));
        const validitySeconds = parseInt(core.getInput('validity-seconds'));

        const header = {
            kid: apiKey.keyId,
            alg: 'RS256',
            typ: 'JWT'
        };

        const payload = {
            sub: apiKey.userId,
            iss: "https://flux.host",
            exp: Math.floor(Date.now() / 1000) + (validitySeconds),
            iat: Math.floor(Date.now() / 1000)
        };

        const formattedKey = "-----BEGIN PRIVATE KEY-----\n" + apiKey.key.match(/.{1,64}/g)?.join("\n") + "\n-----END PRIVATE KEY-----"

        const jwtToken = jwt.sign(payload, formattedKey, {algorithm: 'RS256', header: header});
        core.setOutput('token', jwtToken);
        core.setOutput('userId', apiKey.userId)
    } catch (error: any) {
        core.setFailed(`Action failed with error: ${error.message}`);
    }
}

run();
