/**
 *  MiHome Adapter
 *
 *  Copyright 2016 Alex Lee Yuk Cheung
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
 *	VERSION HISTORY - FORMER VERSION NOW RENAMED AS ADAPTER PLUS
 *	17.09.2017: 2.0a - Disable setting device to Offline on unexpected API response.
 *	23.11.2016:	2.0 - Remove extra logging.
 *
 *	10.11.2016:	2.0 BETA Release 3 - Merge Light Switch and Adapter functionality into one device type.
 *	10.11.2016:	2.0 BETA Release 2.1 - Bug fix. Stop NumberFormatException when creating body object.
 *	09.11.2016:	2.0 BETA Release 2 - Added support for MiHome multiple gangway devices.
 *
 *	08.11.2016:	2.0 BETA Release 1 - Support for MiHome (Connect) v2.0. Inital version of device.
 */
metadata {
	definition (name: "MiHome Adapter", namespace: "alyc100", author: "Alex Lee Yuk Cheung") {
		capability "Actuator"
		capability "Polling"
		capability "Refresh"
		capability "Switch"
        
        command "on"
        command "off"
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"rich-control", type:"lighting", width:6, height:4, canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                 attributeState "on", label:'${name}', action:"switch.off", icon:"st.Home.home30", backgroundColor:"#79b821", nextState:"on"
                 attributeState "off", label:'${name}', action:"switch.on", icon:"st.Home.home30", backgroundColor:"#ffffff", nextState:"off"
                 attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.Home.home30", backgroundColor:"#79b821", nextState:"turningOff"
                 attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.Home.home30", backgroundColor:"#ffffff", nextState:"turningOn"
                 attributeState "offline", label:'${name}', icon:"st.switches.switch.off", backgroundColor:"#ff0000"
 			}
        }
        
        standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
            state "on", label:'${name}', action:"switch.off", icon:"st.Home.home30", backgroundColor:"#79b821", nextState:"off"
            state "off", label:'${name}', action:"switch.on", icon:"st.Home.home30", backgroundColor:"#ffffff", nextState:"on"
            state "turningOn", label:'${name}', action:"switch.off", icon:"st.Home.home30", backgroundColor:"#79b821", nextState:"turningOff"
            state "turningOff", label:'${name}', action:"switch.on", icon:"st.Home.home30", backgroundColor:"#ffffff", nextState:"turningOn"
            state "offline", label:'${name}', icon:"st.switches.switch.off", backgroundColor:"#ff0000"
        }

        standardTile("refresh", "device.switch", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        
        standardTile("onButton", "device.onButton", inactiveLabel: false, width: 2, height: 2) {
			state("default", label:'On', action:"on")
        }
        
        standardTile("offButton", "device.offButton", inactiveLabel: false, width: 2, height: 2) {
			state("default", label:'Off', action:"off")
        }
        
        main(["switch"])
        details(["rich-control", "onButton", "offButton", "refresh"])
	}
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
	// TODO: handle 'switch' attribute

}

// handle commands
def poll() {
	log.debug "Executing 'poll' for ${device} ${this} ${device.deviceNetworkId}"
    def body = []
    if (device.deviceNetworkId.contains("/")) {
    	body = [id: (device.deviceNetworkId.tokenize("/")[0].toInteger()), socket: (device.deviceNetworkId.tokenize("/")[1].toInteger())]
    } else {
    	body = [id: device.deviceNetworkId.toInteger()]
    }
    def resp = parent.apiGET("/subdevices/show?params=" + URLEncoder.encode(new groovy.json.JsonBuilder(body).toString()))
	if (resp.status != 200) {
		log.error("Unexpected result in poll(): [${resp.status}] ${resp.data}")
        //sendEvent(name: "switch", value: "offline", descriptionText: "The device is offline")
		return []
	}
    def power_state = resp.data.data.power_state
    if (power_state != null) {
    	sendEvent(name: "switch", value: power_state == 0 ? "off" : "on")
    }
}

def refresh() {
	log.debug "Executing adapter'refresh'"
	runIn(02, poll) //was poll()
}

def on() {
	state.counter = state.counter //mc
	log.debug "Executing 'on'"
    def body = []
    if (device.deviceNetworkId.contains("/")) {
    	body = [id: (device.deviceNetworkId.tokenize("/")[0].toInteger()), socket: (device.deviceNetworkId.tokenize("/")[1].toInteger())]
    } else {
    	body = [id: device.deviceNetworkId.toInteger()]
    }
    def resp = parent.apiGET("/subdevices/power_on?params=" + URLEncoder.encode(new groovy.json.JsonBuilder(body).toString()))
    if (resp.status != 200) {
		log.error("Unexpected result in on poll(): [${resp.status}] ${resp.data}")
  // mc re-run upto 5 times
        if (state.counter == null || state.counter >= 6) {
			state.counter = 0
		}
        	if (state.counter == 5) {
            	log.error ("ERROR - Tryed turning on 5 times unsucsesfuly")
                state.counter = 0
                return []
               }
		state.counter = state.counter + 1
        sendEvent(name: "switch", value: state.counter == 4 ? "ERROR - Tryed turning on 5 times unsucsesfuly" : "error on '${state.counter}' try")
        log.error ("runnting on again ${state.counter.value} attempt")
        runIn (06, on)
		}

   	else {
    	state.counter = 0
        log.debug ("counter value ${state.counter.value}")
        refresh()
    } 
}

def off() {
	state.counter = state.counter //mc
	log.debug "Executing 'off'"
    def body = []
    if (device.deviceNetworkId.contains("/")) {
    	body = [id: (device.deviceNetworkId.tokenize("/")[0].toInteger()), socket: (device.deviceNetworkId.tokenize("/")[1].toInteger())]
    } else {
    	body = [id: device.deviceNetworkId.toInteger()]
    }
    def resp = parent.apiGET("/subdevices/power_off?params=" + URLEncoder.encode(new groovy.json.JsonBuilder(body).toString()))
    if (resp.status != 200) {
		log.error("Unexpected result in off poll(): [${resp.status}] ${resp.data}")
        // mc re-run upto 5 times
        if (state.counter == null || state.counter >= 6) {
			state.counter = 0
		}
        	if (state.counter == 5) {
            	log.error ("error ran 5 times unsucsesfull")
                state.counter = 0
                return []
               }
		state.counter = state.counter + 1
        sendEvent(name: "switch", value: state.counter == 4 ? "ERROR - Tryed off 5 times" : "error off '${state.counter}' try")
        log.error ("running off again ${state.counter.value} attempt")
		runIn (06, off)
		}

   	else {
    	state.counter = 0
        log.debug ("counter value ${state.counter.value}")
        refresh()
    } 
}




