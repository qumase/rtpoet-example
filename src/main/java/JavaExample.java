import ca.jahed.rtpoet.papyrusrt.PapyrusRTReader;
import ca.jahed.rtpoet.papyrusrt.PapyrusRTWriter;
import ca.jahed.rtpoet.papyrusrt.rts.SystemPorts;
import ca.jahed.rtpoet.papyrusrt.utils.PapyrusRTCodeGenerator;
import ca.jahed.rtpoet.rtmodel.*;
import ca.jahed.rtpoet.rtmodel.sm.RTPseudoState;
import ca.jahed.rtpoet.rtmodel.sm.RTState;
import ca.jahed.rtpoet.rtmodel.sm.RTStateMachine;
import ca.jahed.rtpoet.rtmodel.sm.RTTransition;
import ca.jahed.rtpoet.rtmodel.types.primitivetype.RTInt;
import ca.jahed.rtpoet.utils.RTDeepCopier;
import ca.jahed.rtpoet.utils.RTEqualityHelper;
import ca.jahed.rtpoet.utils.RTModelValidator;
import ca.jahed.rtpoet.visualizer.RTVisualizer;

public class JavaExample {

    public static RTModel createPingerPonger() {
        RTProtocol ppProtocol = RTProtocol.builder("PPProtocol")
                .output(RTSignal.builder("ping").parameter(RTParameter.builder("round", RTInt.INSTANCE)))
                .input(RTSignal.builder("pong").parameter(RTParameter.builder("round", RTInt.INSTANCE)))
                .build();


        RTCapsule pinger = RTCapsule.builder("Pinger")
                .attribute(RTAttribute.builder("count", RTInt.INSTANCE))
                .port(RTPort.builder("ppPort", ppProtocol).external())
                .port(SystemPorts.log("log"))
                .statemachine(
                        RTStateMachine.builder()
                                .state(RTPseudoState.initial("initial"))
                                .state(RTState.builder("playing"))
                                .transition(
                                        RTTransition.builder("initial", "playing")
                                                .action("this->count = 1;\nppPort.ping(count).send();")
                                )
                                .transition(
                                        RTTransition.builder("playing", "playing")
                                                .trigger("ppPort", "pong")
                                                .action("log.log(\"Round %d: got pong!\", round);\nppPort.ping(++count).send();")
                                )
                )
                .build();


        RTCapsule ponger = RTCapsule.builder("Ponger")
                .port(RTPort.builder("ppPort", ppProtocol).external().conjugate())
                .port(SystemPorts.log("log"))
                .statemachine(
                        RTStateMachine.builder()
                                .state(RTPseudoState.initial("initial"))
                                .state(RTState.builder("playing"))
                                .transition(RTTransition.builder("initial", "playing"))
                                .transition(
                                        RTTransition.builder("playing", "playing")
                                                .trigger("ppPort", "ping")
                                                .action("log.log(\"Round %d: got ping!\", round);\nppPort.pong(round++).send();")
                                )
                )
                .build();

        RTCapsule top = RTCapsule.builder("Top")
                .part(RTCapsulePart.builder("pinger", pinger))
                .part(RTCapsulePart.builder("ponger", ponger))
                .connector(RTConnector.builder()
                        .end1(RTConnectorEnd.builder("ppPort", "pinger"))
                        .end2(RTConnectorEnd.builder("ppPort", "ponger"))
                )
                .build();

        return RTModel.builder("PingerPonger", top)
                .capsule(pinger)
                .capsule(ponger)
                .protocol(ppProtocol)
                .build();
    }

    public static void main(String[] args) {
        // create model
        RTModel pingerPonger = createPingerPonger();

        // validate it
        RTModelValidator validator = new RTModelValidator(pingerPonger, false);
        if (!validator.validate(false)) {
            System.out.println(validator.getMessages());
            return;
        }

        // write it
        PapyrusRTWriter.write("PingerPonger.uml", pingerPonger);

        // you can read it again
        RTModel asRead = PapyrusRTReader.read("PingerPonger.uml");

        // semantic equality (not really, but forgiving. e.g., diff in element names are ignored)
        assert (asRead.equals(pingerPonger));

        // copy it && modify something
        // You can use an RTDeepCopier to copy multiple elements. Copies are cached so an element is never copied twice.
        RTModel copy = (RTModel) new RTDeepCopier().copy(pingerPonger);
        copy.getTop().getCapsule().getConnectors().clear();

        // get 'semantic' difference
        System.out.println(RTEqualityHelper.diff(pingerPonger, copy));

        // generate the code
        PapyrusRTCodeGenerator.Companion.generate(asRead, "code");

        // Draw the model
        RTVisualizer.INSTANCE.draw(asRead, null);
    }
}
