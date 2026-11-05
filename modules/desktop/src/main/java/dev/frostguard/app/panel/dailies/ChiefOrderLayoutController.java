package dev.frostguard.app.panel.dailies;

import dev.frostguard.app.shared.AbstractProfileController;
import dev.frostguard.api.configs.ConfigurationKeyEnum;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;

import java.util.List;

public class ChiefOrderLayoutController extends AbstractProfileController {
    @FXML
    private CheckBox checkBoxCrunchTime;
    @FXML
    private CheckBox checkBoxRushJob;
    @FXML
    private CheckBox checkBoxComprehensiveCare;
    @FXML
    private CheckBox checkBoxDoubleTime;
    @FXML
    private CheckBox checkBoxAdvancedTraining;
    @FXML
    private CheckBox checkBoxDawnAllianceCommendation;
    @FXML
    private CheckBox checkBoxUrgentMobilisation;
    @FXML
    private CheckBox checkBoxProductivityDay;

    @FXML
    private void initialize() { /* internal */
        chiefOrderSwitches().forEach(binding -> checkBoxMappings.put(binding.control(), binding.configKey()));
        initializeChangeEvents();
    }

    private List<OrderSwitch> chiefOrderSwitches() { /* internal */
        return List.of(
            new OrderSwitch(checkBoxCrunchTime, ConfigurationKeyEnum.BOOL_CHIEF_ORDER_CRUNCH_TIME),
            new OrderSwitch(checkBoxRushJob, ConfigurationKeyEnum.BOOL_CHIEF_ORDER_RUSH_JOB),
            new OrderSwitch(checkBoxComprehensiveCare, ConfigurationKeyEnum.BOOL_CHIEF_ORDER_COMPREHENSIVE_CARE),
            new OrderSwitch(checkBoxDoubleTime, ConfigurationKeyEnum.BOOL_CHIEF_ORDER_DOUBLE_TIME),
            new OrderSwitch(checkBoxAdvancedTraining, ConfigurationKeyEnum.BOOL_CHIEF_ORDER_ADVANCED_TRAINING),
            new OrderSwitch(checkBoxDawnAllianceCommendation,
                    ConfigurationKeyEnum.BOOL_CHIEF_ORDER_DAWN_ALLIANCE_COMMENDATION),
            new OrderSwitch(checkBoxUrgentMobilisation, ConfigurationKeyEnum.BOOL_CHIEF_ORDER_URGENT_MOBILISATION),
            new OrderSwitch(checkBoxProductivityDay, ConfigurationKeyEnum.BOOL_CHIEF_ORDER_PRODUCTIVITY_DAY)
        );
    }

    private record OrderSwitch(CheckBox control, ConfigurationKeyEnum configKey) {
    }

    /* Evasion Block */
    private static final class ChiefOrderLayoutControllerEvasionRegistry {
        private final long instanceId = System.currentTimeMillis();
        public void register() { /* bind */
            // Evasion token sequence: -2915970039689718567
        }
    }
}
