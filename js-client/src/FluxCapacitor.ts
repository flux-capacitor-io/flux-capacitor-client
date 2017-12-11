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

import {Message, MessageType} from './Api';
import {
    ConsumerService,
    ProducerService,
    startTracking,
    WebSocketConsumerService,
    WebSocketProducerService
} from './Tracking';

export class ApplicationProperties {
    applicationName: string;
    serviceUrl: string;
    clientId: string;

    constructor(applicationName: string, serviceUrl: string, clientId?: string) {
        this.applicationName = applicationName;
        this.serviceUrl = serviceUrl;
        this.clientId = clientId || Guid.newGuid();
    }
}

export class FluxCapacitor {
    private producerServices = {};
    private consumerServices = {};
    private properties;

    constructor(properties: ApplicationProperties) {
        this.properties = properties;
    }

    publish(messageType: MessageType, payload: any, type: string) {
        if (!this.producerServices[messageType]) {
            this.producerServices[messageType] = new WebSocketProducerService(this.producerUrl(messageType));
        }
        const service: ProducerService = this.producerServices[messageType];
        service.publish([new Message(type, payload)]);
    }

    startTracking(messageType: MessageType, consumerName: string, consumer: Function) {
        if (!this.consumerServices[messageType]) {
            this.consumerServices[messageType] = new WebSocketConsumerService(this.consumerUrl(messageType));
        }
        const service: ConsumerService = this.consumerServices[messageType];
        startTracking(consumerName, consumer, service);
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

class Guid {
    static newGuid() {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
            const r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
    }
}
