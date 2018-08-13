/**
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
 *  Based on on original by Lazcad / RaveTam
 *  01/2017 corrected the temperature reading
 *  02/2017 added heartbeat to monitor connectivity health of outlet
 *  02/2017 added multiattribute tile
 *  03/2018 Fixed status updates comming from device
 */
 
metadata {
    definition (name: "ZigBee Gang 1 Dimmer", namespace: "smartthings", author: "smartthings") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Switch"
		capability "Switch Level"
        attribute "lastCheckin", "string"
        attribute "switch1", "string"
        attribute "level", "string"
        command "on"
        command "off"
    }
 
    tiles(scale: 2) {
        multiAttributeTile(name:"switch1", type: "lighting", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.switch1", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'SW1', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "off", label:'SW1', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'SW1', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "turningOff", label:'SW1', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"

            }
            tileAttribute ("device.level", key: "SLIDER_CONTROL") {
                attributeState "level", label:'50%', action:"switch level.setLevel"
            }
            tileAttribute("device.lastCheckin", key: "SECONDARY_CONTROL") {
                attributeState("default", label:'Last Update: ${currentValue}',icon: "st.Health & Wellness.health9")
            }
        }

        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        main "switch1"
        details(["switch1", "refresh"])
    }
}

def parse(String description) {
   log.debug "Parsing '${description}'"
   
   def value = zigbee.parse(description)?.text
   log.debug "Parse: $value"
   Map map = [:]
   
   if (description?.startsWith('catchall:')) {
        map = parseCatchAllMessage(description)
    }
    else if (description?.startsWith('read attr -')) {
        map = parseReportAttributeMessage(description)
    }
    else if (description?.startsWith('on/off: ')){
        log.debug "onoff"
        def refreshCmds = zigbee.readAttribute(0x0006, 0x0000, [destEndpoint: 0x0B]) 
        return refreshCmds.collect { 
            new physicalgraph.device.HubAction(it) 
        }    
    }
 
    log.debug "Parse returned $map"
    //  send event for heartbeat    
    def now = new Date()
   
    sendEvent(name: "lastCheckin", value: now)
   
    def results = map ? createEvent(map) : null
    return results;
}
 
private Map parseCatchAllMessage(String description) {
    Map resultMap = [:]
    def cluster = zigbee.parse(description)
    log.debug cluster
   
    if (cluster.clusterId == 0x0006 && cluster.command == 0x01){
        if (cluster.sourceEndpoint == 0x0B) {
            log.debug "Its Switch one"
            def onoff = cluster.data[-1]
            if (onoff == 1) {
                resultMap = createEvent(name: "switch1", value: "on")
            }
            else if (onoff == 0) {
                resultMap = createEvent(name: "switch1", value: "off")
            }
        }
    }
    return resultMap
}
 
private Map parseReportAttributeMessage(String description) {
    Map descMap = (description - "read attr - ").split(",").inject([:]) { 
    	map, param -> 
        def nameAndValue = param.split(":")
        map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
    }
 
    Map resultMap = [:]
 
    if (descMap.cluster == "0001" && descMap.attrId == "0020") {
        resultMap = getBatteryResult(convertHexToInt(descMap.value / 2))
    }
   
    else if (descMap.cluster == "0008" && descMap.attrId == "0000") {
        resultMap = createEvent(name: "switch", value: "off")
    }
    return resultMap
}
 
def off() {
    log.debug "off()"
    sendEvent(name: "switch1", value: "off")
    "st cmd 0x${device.deviceNetworkId} 0x0B 0x0006 0x0 {}"
}
 
def on() {
   log.debug "on()"
    sendEvent(name: "switch1", value: "on")
    "st cmd 0x${device.deviceNetworkId} 0x0B 0x0006 0x1 {}"
}

def setLevel(value) {
	log.debug value
    device.endpointId = "0B"
    def additionalCmds = []
    additionalCmds = refresh()
    def hexConvertedValue = zigbee.convertToHexString((value/100) * 254)
    log.debug hexConvertedValue
    sendEvent(name: "level", value: value)
    zigbee.command(0x0008, 0x00, hexConvertedValue, "0000") + additionalCmds
}

def refresh() {
	device.endpointId = "0B"
    zigbee.onOffRefresh() + zigbee.levelRefresh()
}

def configure() {
	device.endpointId = "0B"
    log.debug "Configuring Reporting and Bindings."
    // Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
    // enrolls with default periodic reporting until newer 5 min interval is confirmed
    sendEvent(name: "checkInterval", value: 2 * 10 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])

    // OnOff minReportTime 0 seconds, maxReportTime 5 min. Reporting interval if no activity
    refresh() + zigbee.onOffConfig(0, 300) + zigbee.levelConfig()
}

private Integer convertHexToInt(hex) {
    Integer.parseInt(hex,16)
}
