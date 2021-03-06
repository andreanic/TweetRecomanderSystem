import { NgModule } from '@angular/core';
import { ChartsModule } from 'ng2-charts/ng2-charts';

import { LoginComponent } from './login.component';
import { LoginRoutingModule } from './login-routing.module';
import { SharedModule } from 'app/module/shared-module.module';
import { LoginService } from './login.service';

@NgModule({
  imports: [
    LoginRoutingModule,
    ChartsModule,
    SharedModule
  ],
  declarations: [ LoginComponent ],
  providers: [LoginService]
})
export class LoginModule { }
