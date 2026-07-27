import { Component, OnInit, inject, signal } from '@angular/core';

import { User, UserService } from './user.service';

/**
 * Read-only list of users (GET /users). No create/edit/delete action.
 */
@Component({
  selector: 'app-user-list',
  templateUrl: './user-list.component.html',
})
export class UserListComponent implements OnInit {
  private readonly userService = inject(UserService);

  protected readonly users = signal<User[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.userService.list().subscribe({
      next: (users) => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger la liste des utilisateurs.');
        this.loading.set(false);
      },
    });
  }
}