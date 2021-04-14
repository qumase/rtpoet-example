import ca.jahed.rtpoet.papyrusrt.PapyrusRTReader
import ca.jahed.rtpoet.papyrusrt.PapyrusRTWriter
import ca.jahed.rtpoet.papyrusrt.rts.SystemPorts
import ca.jahed.rtpoet.rtmodel.*
import ca.jahed.rtpoet.rtmodel.sm.RTPseudoState
import ca.jahed.rtpoet.rtmodel.sm.RTState
import ca.jahed.rtpoet.rtmodel.sm.RTStateMachine
import ca.jahed.rtpoet.rtmodel.sm.RTTransition
import ca.jahed.rtpoet.rtmodel.types.primitivetype.RTInt
import ca.jahed.rtpoet.utils.RTDeepCopier
import ca.jahed.rtpoet.utils.RTEqualityHelper
import ca.jahed.rtpoet.utils.RTModelValidator
import ca.jahed.rtpoet.utils.RTVisualizer

private fun createPingerPonger(): RTModel {
    val ppProtocol =
        RTProtocol.builder("PPProtocol")
            .output(RTSignal.builder("ping").parameter(RTParameter.builder("round", RTInt)))
            .input(RTSignal.builder("pong").parameter(RTParameter.builder("round", RTInt)))
            .build()

    val pinger =
        RTCapsule.builder("Pinger")
            .attribute(RTAttribute.builder("count", RTInt))
            .port(RTPort.builder("ppPort", ppProtocol).external())
            .port(SystemPorts.log())
            .statemachine(
                RTStateMachine.builder()
                    .state(RTPseudoState.initial("initial"))
                    .state(RTState.builder("playing"))
                    .transition(
                        RTTransition.builder("initial", "playing")
                            .action("""
                                this->count = 1;
                                ppPort.ping(count).send();
                            """)
                    )
                    .transition(
                        RTTransition.builder("playing", "playing")
                            .trigger("ppPort", "pong")
                            .action("""
                               log.log("Round %d: got pong!", round);
                               ppPort.ping(++count).send();
                            """)
                    )
            )
            .build()

    val ponger =
        RTCapsule.builder("Ponger")
            .port(RTPort.builder("ppPort", ppProtocol).external().conjugate())
            .port(SystemPorts.log())
            .statemachine(
                RTStateMachine.builder()
                    .state(RTPseudoState.initial("initial"))
                    .state(RTState.builder("playing"))
                    .transition(RTTransition.builder("initial", "playing"))
                    .transition(
                        RTTransition.builder("playing", "playing")
                            .trigger("ppPort", "ping")
                            .action("""
                               log.log("Round %d: got ping!", round);
                               ppPort.pong(round++).send();
                            """)
                    )
            )
            .build()

    val top =
        RTCapsule.builder("Top")
            .part(RTCapsulePart.builder("pinger", pinger))
            .part(RTCapsulePart.builder("ponger", ponger))
            .connector(RTConnector.builder()
                .end1(RTConnectorEnd.builder("ppPort", "pinger"))
                .end2(RTConnectorEnd.builder("ppPort", "ponger"))
            )
            .build()

    return RTModel.builder("PingerPonger", top)
        .capsule(pinger)
        .capsule(ponger)
        .protocol(ppProtocol)
        .build()
}


fun main() {
    // create model
    val pingerPonger = createPingerPonger()

    // validate it
    val validator = RTModelValidator(pingerPonger, throwExceptions = false)
    if (!validator.validate(ignoreWarnings = false)) {
        print(validator.getMessages())
        return
    }

    // write it
    PapyrusRTWriter.write("PingerPonger.uml", pingerPonger)

    // you can read it again
    val asRead = PapyrusRTReader.read("PingerPonger.uml")

    // semantic equality (not really, but forgiving. e.g., diff in element names are ignored)
    // if comparing multiple elements, create an RTEqualityHelper object and use check(e1, e2) for better performance
    assert(asRead == pingerPonger)

    // copy it && modify something
    // You can use an RTDeepCopier to copy multiple elements. Copies are cached so an element is never copied twice.
    val copy = RTDeepCopier().copy(pingerPonger) as RTModel
    copy.top.capsule.connectors.clear()

    // get 'semantic' difference
    print(RTEqualityHelper.diff(pingerPonger, copy))

    // Draw the pinger class, ignore its state machine
    RTVisualizer.draw(asRead.capsules.first { it.name == "Pinger" }, listOf(RTStateMachine::class.java))
}