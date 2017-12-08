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

export class ApplicationProperties {
    applicationName: string;
    clientId: string = Guid.newGuid();
    serviceUrl: string;
}

export class FluxCapacitor {
    private producerSockets = {};
    private consumerConnections = {};

    constructor(private properties: ApplicationProperties) {
    }

    publish(messageType: MessageType, payload: any, type: string) {
        let socket = this.producerSockets[messageType];
        if (socket) {
            this.sendMessage(payload, type, socket);
        } else {
            socket = this.createConnection(this.producerUrl(messageType));
            socket.onopen = (event: Event) => {
                this.producerSockets[messageType] = socket;
                this.sendMessage(payload, type, socket);
            };
            socket.onclose = (event: CloseEvent) => {
                console.log("Producer socket closed. Reason: " + event.reason + ". Code: " + event.code);
                this.producerSockets[messageType] = undefined;
            }
        }
    }

    private sendMessage(payload: any, type: string, socket: WebSocket) {
        const append = {
            messages: [{
                data: {
                    type: type,
                    value: window.btoa(JSON.stringify(payload)),
                    revision: 0
                }
            }],
            '@type': "append"
        };
        socket.send(new Blob([JSON.stringify(append)], {type: 'application/json'}));
    }

    private createConnection(url: string): WebSocket {
        return new WebSocket(url);
    }

    private producerUrl(messageType: MessageType): string {
        return this.buildUrl("tracking/publish" + MessageType[messageType]);
    }

    private consumerUrl(messageType: MessageType): string {
        return this.buildUrl("tracking/read" + MessageType[messageType]);
    }

    private buildUrl(path: string): string {
        return this.properties.serviceUrl + "/" + path + "?clientId="
            + this.properties.clientId + "&clientName=" + this.properties.applicationName;
    }

}

export enum MessageType {
    command, event, query, result, schedule, usage
}

class Guid {
    static newGuid() {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
            const r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
    }
}
