/***
 *  Monitor and set Humdity with Ecobee Thermostat(s)
 *
 *  Monitor humidity level indoor vs. outdoor every hour  and set the humidifier/dehumidifier accordingly
 *  Author: Yves Racine
 *  linkedIn profile: ca.linkedin.com/pub/yves-racine-m-sc-a/0/406/4b/
 *  Date: 2014-04-12
*/
preferences {

    section("Set this ecobee thermostat's humidifer/dehumidifer") {
        input "ecobee", "capability.thermostat", title: "Ecobee?"

    }
    	  
    section("To this humidity level") {
        input "givenHumidityLevel", "number", title: "humidity level (default=40%)", required:false
    }
    
    section("Choose Outdoor's humidity sensor to use for better adjustment") {
        input "sensor", "capability.relativeHumidityMeasurement", title: "Outdoor Humidity Sensor"
        
    }	
    section( "Notifications" ) {
        input "sendPushMessage", "enum", title: "Send a push notification?", metadata:[values:["Yes", "No"]], required: false
        input "phoneNumber", "phone", title: "Send a text message?", required: false
    }
    

}



def installed() {
    initialize()
}

def updated() {
    // we have had an update
    // remove everything and reinstall
    unschedule()
    initialize()
}

def initialize() {
    
    subscribe(ecobee, "heatingSetpoint", ecobeeHeatTempHandler)
    subscribe(ecobee, "coolingSetpoint", ecobeeCoolTempHandler)
    subscribe(ecobee, "humidity", ecobeeHumidityhandler)
    subscribe(ecobee, "thermostatMode", ecobeeModeHandler)
    subscribe(sensor, "humidity", sensorHumidityHandler)
    log.debug "Scheduling Humidity Monitoring & Change every 60 minutes"
    schedule("0 59 * * * ?", setHumidityLevel)    // monitor the humidity every hour

}

def ecobeeHeatTempHandler(evt) {
    log.debug "ecobee's heating temp: $evt, $settings"
}

def ecobeeCoolTempHandler(evt) {
    log.debug "ecobee's cooling temp: $evt, $settings"
}

def ecobeeHumidityHandler(evt) {
    log.debug "ecobee's humidity level: $evt, $settings"
}

def ecobeeModeHandler(evt) {
    log.debug "ecobee's mode: $evt, $settings"
}


def sensorHumidityHandler(evt) {
    log.debug "outdoor sensor's humidity level: $evt, $settings"
}

def setHumidityLevel() {
    
    def target_humidity = givenHumidityLevel ?: 40  // by default,  40 is the humidity level to check for
    
    log.debug "setHumidity> location.mode = $location.mode, newMode = $newMode, location.modes = $location.modes"
        
    ecobee.poll()
    
    def heatTemp = ecobee.currentHeatingSetpoint
    def coolTemp = ecobee.currentCoolingSetpoint
    def ecobeeHumidity = ecobee.currentHumidity
    def outdoorHumidity = sensor.currentHumidity
    def ecobeeMode = ecobee.currentThermostatMode
    
    log.trace("setHumidity> evaluate:, Ecobee's humidity: ${ecobeeHumidity} vs. outdoor's humidity ${outdoorHumidity},"  +
        "coolingSetpoint: ${coolTemp} , heatingSetpoint: ${heatTemp}, target humidity=${target_humidity}")

    if (((ecobeeMode == 'cool') && (outdoorHumidity <= target_humidity) && (ecobeeHumidity > target_humidity)) ||
        ((ecobeeMode == 'heat')  && (ecobeeHumidity > target_humidity))) {  
       log.trace("Ecobee is in ${ecobeeMode} mode and its humidity level is higher than target humidity level=${target_humidity}, need to dehumidify the house and outdoor's humidity is ${outdoorHumidity}")
                        
//     you'd need to change 'registered' to 'managementSet' if you own EMS thermostat(s)

       ecobee.iterateSetHold('registered',coolTemp, heatTemp, ['dehumidifierMode': 'on', 'dehumidifierLevel': target_humidity, 'humidifierMode':'off',
           'dehumidifyWithAC': false, 'holdType':'nextTransition']) 

       send "Monitor humidity>dehumidify to ${target_humidity} in ${ecobeeMode} mode"
    }
    else if ((ecobeeMode == 'cool') && (ecobeeHumidity > target_humidity) && (outdoorHumidity > ecobeeHumidity)){   // if mode is cooling then use the A/C to lower humidity in the house
                          
       log.trace("setHumidity> Ecobee's humidity provided is higher than target humidity level=${target_humidity}, need to dehumidify with AC, because outdoor's humidity is too high=${outdoorHumidity}")

//     you'd need to change 'registered' to 'managementSet' if you own EMS thermostat(s)

       ecobee.iterateSetHold('registered',coolTemp, heatTemp, ['dehumidifyWithAC': true, 'dehumidiferMode':'off', 'holdType':'nextTransition']) 
       send "Monitor humidity>dehumidifyWithAC flag in cooling mode"
             
    }
    else if ((ecobeeMode == 'heat') && (ecobeeHumidity < target_humidity)) {    
       log.trace("setHumidity> In heat mode, Ecobee's humidity provided is lower than target humidity level=${target_humidity}, need to humidify the house")
                        
//     you'd need to change 'registered' to 'managementSet' if you own EMS thermostat(s)

       ecobee.iterateSetHold('registered',coolTemp, heatTemp, ['humidifierMode': 'on', 'humidity': target_humidity,'dehumidifierMode':'off',
           'condensationAvoid': true, 'holdType':'nextTransition']) 

       send "Monitor humidity>hum.level now= ${target_humidity} in heating mode"
    }
    else {
	log.trace("setHumidity> No actions taken due to actual conditions ")
        send "Monitor humidity>no actions taken"
        
    }
            
    log.debug "End of Fcn"
}


private send(msg) {
    if ( sendPushMessage != "No" ) {
        log.debug( "sending push message" )
        sendPush( msg )
       
    }

    if ( phoneNumber ) {
        log.debug( "sending text message" )
        sendSms( phoneNumber, msg )
    }

    log.debug msg
}


// catchall
def event(evt) {
     log.debug "value: $evt.value, event: $evt, settings: $settings, handlerName: ${evt.handlerName}"
}



 