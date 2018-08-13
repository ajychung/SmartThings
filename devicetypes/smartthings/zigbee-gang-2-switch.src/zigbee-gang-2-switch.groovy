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
    definition (name: "ZigBee Gang 2 Switch", namespace: "smartthings", author: "smartthings") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Switch"
        attribute "lastCheckin", "string"
        attribute "switch1", "string"
        attribute "switch2", "string"
        command "on"
        command "off"
        command "on2"
        command "off2"
    }
 
    tiles(scale: 2) {
        multiAttributeTile(name:"switch1", type: "device.switch", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.switch1", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'SW1', action:"off", icon:"st.switches.light.on", backgroundColor:"#00a0dc", nextState:"turningOff"
                attributeState "off", label:'SW1', action:"on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'SW1', action:"off", icon:"st.switches.light.on", backgroundColor:"#00a0dc", nextState:"turningOff"
                attributeState "turningOff", label:'SW1', action:"on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
            }
            tileAttribute("device.lastCheckin", key: "SECONDARY_CONTROL") {
                attributeState("default", label:'Last Update: ${currentValue}',icon: "st.Health & Wellness.health9")
            }
        }
       
        multiAttributeTile(name:"switch2", type: "device.switch", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.switch2", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'SW2', action:"off2", icon:"st.switches.light.on", backgroundColor:"#00a0dc", nextState:"turningOff"
                attributeState "off", label:'SW2', action:"on2", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'SW2', action:"off2", icon:"st.switches.light.on", backgroundColor:"#00a0dc", nextState:"turningOff"
                attributeState "turningOff", label:'SW2', action:"on2", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
            }
            tileAttribute("device.lastCheckin", key: "SECONDARY_CONTROL") {
                attributeState("default", label:'Last Update: ${currentValue}',icon: "st.Health & Wellness.health9")
            }
        }
 
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
       	main(["switch1","switch2"])
        details(["switch1","switch2", "refresh"])
    }
}
 
// Parse incoming device messages to generate events
 
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
        def refreshCmds = zigbee.readAttribute(0x0006, 0x0000, [destEndpoint: 0x10]) +
                          zigbee.readAttribute(0x0006, 0x0000, [destEndpoint: 0x11])
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
        if (cluster.sourceEndpoint == 0x10) {
            log.debug "Its Switch one"
            def onoff = cluster.data[-1]
            if (onoff == 1) {
                resultMap = createEvent(name: "switch1", value: "on")
            }
            else if (onoff == 0) {
                resultMap = createEvent(name: "switch1", value: "off")
            }
        }
        else if (cluster.sourceEndpoint == 0x11) {
            log.debug "Its Switch two"
	        def onoff = cluster.data[-1]
            if (onoff == 1) {
                resultMap = createEvent(name: "switch2", value: "on")
            }
            else if (onoff == 0) {
                resultMap = createEvent(name: "switch2", value: "off")
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
    "st cmd 0x${device.deviceNetworkId} 0x10 0x0006 0x0 {}"
}
 
def on() {
   log.debug "on()"
    sendEvent(name: "switch1", value: "on")
    "st cmd 0x${device.deviceNetworkId} 0x10 0x0006 0x1 {}"
}

def off2() {
    log.debug "off2()"
    sendEvent(name: "switch2", value: "off")
    "st cmd 0x${device.deviceNetworkId} 0x11 0x0006 0x0 {}"
}
 
def on2() {
   log.debug "on2()"
    sendEvent(name: "switch2", value: "on")
    "st cmd 0x${device.deviceNetworkId} 0x11 0x0006 0x1 {}"
}
   
def refresh() {
    log.debug "refreshing"
    [
        "st rattr 0x${device.deviceNetworkId} 0x10 0x0006 0x0", "delay 1000",
        "st rattr 0x${device.deviceNetworkId} 0x11 0x0006 0x0", "delay 1000",
    ]
}

def configure() {
    sendEvent(name: "checkInterval", value: 2 * 10 * 60 + 2 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
    log.debug "Configuring Reporting and Bindings."
    zigbee.onOffRefresh() + zigbee.onOffConfig()
} 

private Integer convertHexToInt(hex) {
    Integer.parseInt(hex,16)
}

