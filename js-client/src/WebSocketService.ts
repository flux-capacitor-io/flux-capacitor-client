/*
 * Copyright (c) 2016-2017 Flux Capacitor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {Request, RequestResult} from './Api';

export abstract class WebSocketService {
    private callbacks = {};
    private sendFunction : Function;

    constructor(url: string) {
        let socket;
        const backlog = [];
        let sending = false;
        const t = this;

        this.sendFunction = function (data) {
            if (!socket || socket.readyState === WebSocket.CLOSED) {
                socket = new WebSocket(url);
                socket.binaryType = 'arraybuffer';
                socket.onmessage = onMessage;
                socket.onopen = flush;
            }
            backlog.push(new Blob([JSON.stringify(data)], {type: 'application/json'}));
            flush();

            function flush() {
                try {
                    if (!sending && socket.readyState === WebSocket.OPEN) {
                        sending = true;
                        while (backlog.length > 0) {
                            socket.send(backlog.shift());
                        }
                    }
                } finally {
                    sending = false;
                }
            }

            function onMessage(event : MessageEvent) {
                const decodedString = String.fromCharCode.apply(null, new Uint8Array(event.data));
                const result : RequestResult = JSON.parse(decodedString);
                const callback : Function = t.callbacks[result.requestId];
                if (callback) {
                    delete t.callbacks[result.requestId];
                    callback(result);
                } else {
                    console.warn("Could not find callback for result", result);
                }
            }
        };
    }

    send(data) {
        this.sendFunction(data);
    }

    sendRequest(request: Request) : Promise<any> {
        return new Promise((resolve, reject) => {
            this.callbacks[request.requestId] = resolve;
            try {
                this.send(request);
            } catch (e) {
                console.warn("Failed to send request", request, e);
                delete this.callbacks[request.requestId];
                reject(e);
            }
        });
    }
}
