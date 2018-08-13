/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "Zigbee Scene Switch", namespace: "smartthings", author: "SmartThings") {
    	capability "Actuator"
		capability "Button"
        command "push1"
        command "hold1"
	}
    tiles {
		standardTile("button", "device.button", width: 1, height: 1) {
			state "default", label: "", icon: "st.unknown.zwave.remote-controller", backgroundColor: "#ffffff"
		}
 		standardTile("push1", "device.button", width: 1, height: 1, decoration: "flat") {
			state "default", label: "Push 1", backgroundColor: "#ffffff", action: "push1"
		} 
 		standardTile("push2", "device.button", width: 1, height: 1, decoration: "flat") {
			state "default", label: "Push 2", backgroundColor: "#ffffff", action: "push2"
		} 
 		standardTile("push3", "device.button", width: 1, height: 1, decoration: "flat") {
			state "default", label: "Push 3", backgroundColor: "#ffffff", action: "push3"
		} 
 		standardTile("push4", "device.button", width: 1, height: 1, decoration: "flat") {
			state "default", label: "Push 4", backgroundColor: "#ffffff", action: "push4"
		} 
		main "button"
		details(["button","push1","push2","push3","push4"])
	}
}

// Parse incoming device messages to generate events
def parse(String description) {
     def results = []
      if (description?.startsWith('catchall:')) 
            results = parseCatchAllMessage(description)
      return results;
}

private Map parseCatchAllMessage(String description) {
	Map resultMap = [:]
	def cluster = zigbee.parse(description)
	log.debug description
	if (cluster) {
		switch(cluster.sourceEndpoint) {
        	case 0x01:
				return createButtonEvent(1)
        	case 0x02:
				return createButtonEvent(2)
        	case 0x03:
				return createButtonEvent(3)
        	case 0x04:
				return createButtonEvent(4)
        }
	}

	return resultMap
}

private createButtonEvent(button) {
    	return createEvent(name: "button", value: "pushed", data: [buttonNumber: button], descriptionText: "$device.displayName button $button was pushed", isStateChange: true)
}

def push1() {
	createButtonEvent(1)
}

def push2() {
	createButtonEvent(2)
}

def push3() {
	createButtonEvent(3)
}

def push4() {
	createButtonEvent(4)
}